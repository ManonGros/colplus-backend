package org.col.admin.importer.neo;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;
import com.google.common.collect.UnmodifiableIterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.col.admin.importer.IdGenerator;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NodeBatchProcessor.BatchConsumer;
import org.col.admin.importer.neo.model.*;
import org.col.admin.importer.neo.traverse.StartEndHandler;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.col.admin.importer.reference.ReferenceStore;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.common.mapdb.MapDbObjectSerializer;
import org.col.common.text.StringUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.neo4j.graphalgo.LabelPropagationProc;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A persistence mechanism for storing core taxonomy & names propLabel and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large propLabel in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node value.
 */
public class NeoDb implements ReferenceStore {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDb.class);

  private final int datasetKey;
  private final GraphDatabaseBuilder neoFactory;
  private final DB mapDb;
  private final Atomic.Var<Dataset> dataset;
  // ID -> REF
  private final Map<String, Reference> references;
  // Citation -> refID
  private final Map<String, String> refIndexCitation;
  // verbatimKey sequence and lookup
  private final AtomicInteger verbatimSequence = new AtomicInteger(0);
  private final Map<Integer, VerbatimRecord> verbatim;
  
  private final File neoDir;
  private final KryoPool pool;
  private BatchInserter inserter;
  public final int batchSize;
  public final int batchTimeout;
  private final NeoNameStore names;
  private final NeoUsageStore usages;

  private final String idGenPrefix = ".neodb.";
  private IdGenerator idGen = new IdGenerator(idGenPrefix);
  private GraphDatabaseService neo;
  private final AtomicInteger neoCounter = new AtomicInteger(0);

  /**
   * @param mapDb
   * @param neoDir
   * @param neoFactory
   * @param batchTimeout in minutes
   */
  NeoDb(int datasetKey, DB mapDb, File neoDir, GraphDatabaseBuilder neoFactory, int batchSize, int batchTimeout) {
    this.datasetKey = datasetKey;
    this.neoFactory = neoFactory;
    this.neoDir = neoDir;
    this.mapDb = mapDb;
    this.batchSize = batchSize;
    this.batchTimeout = batchTimeout;

    try {
      pool = new KryoPool.Builder(new NeoKryoFactory())
          .softReferences()
          .build();
      dataset = (Atomic.Var<Dataset>) mapDb.atomicVar("dataset", new MapDbObjectSerializer(Dataset.class, pool, 256))
          .createOrOpen();
      verbatim = mapDb.hashMap("verbatim")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(VerbatimRecord.class, pool, 128))
          .createOrOpen();
      references = mapDb.hashMap("references")
          .keySerializer(Serializer.STRING)
          .valueSerializer(new MapDbObjectSerializer(Reference.class, pool, 128))
          .createOrOpen();
      refIndexCitation = mapDb.hashMap("refIndexCitation")
          .keySerializer(Serializer.STRING_ASCII)
          .valueSerializer(Serializer.STRING)
          .createOrOpen();
      
      openNeo();
      
      usages = new NeoUsageStore(mapDb, "usages", pool, idGen, this);
      
      names = new NeoNameStore(mapDb, "names", pool, idGen, this);
  
  
    } catch (Exception e) {
      LOG.error("Failed to initialize a new NeoDB", e);
      close();
      throw e;
    }
  }

  /**
   * Fully closes the dao leaving any potentially existing persistence files untouched.
   */
  public void close() {
    try {
      if (mapDb != null && !mapDb.isClosed()) {
        mapDb.close();
      }
    } catch (Exception e) {
      LOG.error("Failed to close mapDb for directory {}", neoDir.getAbsolutePath(), e);
    }
    closeNeo();
    LOG.debug("Closed NormalizerStore for directory {}", neoDir.getAbsolutePath());
  }

  public void closeAndDelete() {
    close();
    if (neoDir != null && neoDir.exists()) {
      LOG.debug("Deleting neo4j & mapDB directory {}", neoDir.getAbsolutePath());
      FileUtils.deleteQuietly(neoDir);
    }
  }

  private void openNeo() {
    LOG.debug("Starting embedded neo4j database from {}", neoDir.getAbsolutePath());
    neo = neoFactory.newGraphDatabase();
    try {
      GraphDatabaseAPI gdb = (GraphDatabaseAPI) neo;
      gdb.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(UnionFindProc.class);
      gdb.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(LabelPropagationProc.class);

    } catch (KernelException e) {
      LOG.warn("Unable to register neo4j algorithms", e);
    }
  }

  private void closeNeo() {
    try {
      if (neo != null) {
        neo.shutdown();
      }
    } catch (Exception e) {
      LOG.error("Failed to close neo4j {}", neoDir.getAbsolutePath(), e);
    }
  }

  public GraphDatabaseService getNeo() {
    return neo;
  }

  public void setIdGeneratorPrefix(String prefix) {
    if (this.idGen.getCounter() > 0) {
      // we had issues ids already with the previous generator, continue with its

    }
    this.idGen = Preconditions.checkNotNull(idGen);
    // update previous ids
  }
  
  public Dataset getDataset() {
    return dataset.get();
  }
  
  public NeoNameStore names() {
    return names;
  }
  
  public NeoCRUDStore<NeoUsage> usages() {
    return usages;
  }
  
  public NeoUsage usageWithName(Node n) {
    NeoUsage u = usages().objByNode(n);
    NeoName nn = nameByUsage(n);
    u.usage.setName(nn.name);
    u.nameNode = nn.node;
    return u;
  }
  
  /**
   * @return a collection of all name relations with name key using node ids.
   */
  public NameRelation toRelation(Relationship r) {
    NameRelation nr = new NameRelation();
    nr.setDatasetKey(datasetKey);
    nr.setType(RelType.valueOf(r.getType().name()).nomRelType);
    nr.setNameId(usages.objByNode(r.getStartNode()).getId());
    nr.setRelatedNameId(usages.objByNode(r.getEndNode()).getId());
    nr.setNote((String) r.getProperty(NeoProperties.NOTE, null));
    nr.setPublishedInId((String) r.getProperty(NeoProperties.REF_ID, null));
    if (r.hasProperty(NeoProperties.VERBATIM_KEY)) {
      nr.setVerbatimKey((Integer) r.getProperty(NeoProperties.VERBATIM_KEY));
    }
    return nr;
  }
  
  /**
   * @return a collection of all name relations with key using node ids.
   */
  public List<NameRelation> relations(Node n) {
    return Iterables.stream(n.getRelationships())
        .filter(r -> RelType.valueOf(r.getType().name()).nomRelType != null)
        .map(this::toRelation)
        .collect(Collectors.toList());
  }
  
  public List<Node> taxaByScientificName(String scientificName, Rank rank, boolean inclUnranked) {
    List<Node> names = names().nodesByName(scientificName);
    names.removeIf(n -> {
      Rank r = NeoProperties.getRank(n, Rank.UNRANKED);
      if (inclUnranked) {
        return !r.equals(rank) && r != Rank.UNRANKED;
      } else {
        return !r.equals(rank);
      }
    });
  
    List<Node> taxa = new ArrayList<>();
    for (Node n : names) {
      taxa.addAll(usageNodesByName(n));
    }
    return taxa;
  }

  /**
   * Process all nodes in batches with the given callback handler.
   * Every batch is processed in a single transaction which is committed at the end of the batch.
   * To avoid nested flat transactions we execute all batches in a separate consumer thread.
   *
   * If new nodes are created within a batch transaction this will be also be returned to the callback handler at the very end.
   *
   * Iteration is by node value starting from node value 1 to highest.
   *
   * @param label neo4j node label to select nodes by. Use NULL for all nodes
   * @param batchSize
   * @param callback
   * @return total number of processed nodes.
   */
  public int process(@Nullable Labels label, final int batchSize, NodeBatchProcessor callback) {
    final BlockingQueue<List<Node>> queue = new LinkedBlockingQueue<>(3);
    BatchConsumer consumer = new BatchConsumer(datasetKey, neo, callback, queue, Thread.currentThread());
    Thread consThread = new Thread(consumer, "neodb-processor-"+datasetKey);
    consThread.start();

    try (Transaction tx = neo.beginTx()){
      final ResourceIterator<Node> iter = label == null ? neo.getAllNodes().iterator() : neo.findNodes(label);
      UnmodifiableIterator<List<Node>> batchIter = com.google.common.collect.Iterators.partition(iter, batchSize);

      while (batchIter.hasNext() && consThread.isAlive()) {
        checkIfInterrupted();
        List<Node> batch = batchIter.next();
        if (!queue.offer(batch, batchTimeout, TimeUnit.MINUTES)) {
          LOG.error("Failed to offer new batch {} of size {} within {} minutes for neodb processing by {}", consumer.getBatchCounter(), batch.size(), batchTimeout, callback);
          LOG.info("Nodes: {}", batch.stream()
              .map(NeoProperties::getScientificNameWithAuthor)
              .collect(Collectors.joining( "; " ))
          );
          throw new RuntimeException("Failed to offer new batch for neodb processing by " + callback);
        }
      }
      if (consThread.isAlive()) {
        queue.put(BatchConsumer.POISON_PILL);
      }
      consThread.join();

      // mark good for commit
      tx.success();
      LOG.info("Neo processing of {} finished in {} batches with {} records", label, consumer.getBatchCounter(), consumer.getRecordCounter());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();  // set interrupt flag back
      LOG.error("Neo processing interrupted", e);

      if (consThread.isAlive()) {
        consThread.interrupt();
      }
    }

    if (consumer.hasError()) {
      throw consumer.getError();
    }

    return consumer.getRecordCounter();
  }

  private void checkIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Neo thread was cancelled/interrupted");
    }
  }

  /**
   * Shuts down the regular neo4j db and opens up neo4j in batch mode.
   * While batch mode is active only writes will be accepted and reads from the store
   * will throw exceptions.
   */
  public void startBatchMode() {
    try {
      closeNeo();
      inserter = BatchInserters.inserter(neoDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isBatchMode() {
    return inserter != null;
  }

  public void endBatchMode() throws NotUniqueRuntimeException {
    inserter.shutdown();
    openNeo();
    inserter = null;
  }
  
  /**
   * Creates both a name and a usage neo4j node.
   * The name node is returned while the usage node is set on the NeoUsage object.
   * The name instance is taken from the usage object which is removed from the usage.
   * @return the created name node or null if it could not be created
   */
  public Node createNameAndUsage(NeoUsage u) {
    Preconditions.checkArgument(u.getNode() == null, "NeoUsage already has a neo4j node");
    Preconditions.checkArgument(u.nameNode == null, "NeoUsage already has a neo4j name node");
    Preconditions.checkNotNull(u.usage.getName(), "NeoUsage with name required");
    
    // first create the name in a new node
    NeoName nn = new NeoName(u.usage.getName());
    if (nn.getId() == null) {
      nn.setId(u.getId());
    }
    if (nn.getVerbatimKey() == null) {
      nn.setVerbatimKey(u.getVerbatimKey());
    }
    if (nn.name.getOrigin() == null) {
      if (u.isSynonym()) {
        nn.name.setOrigin(u.getSynonym().getOrigin());
      } else {
        nn.name.setOrigin(u.getTaxon().getOrigin());
      }
    }
    nn.homotypic = u.homotypic;
    u.nameNode = names.create(nn);
  
    if (u.nameNode != null) {
      // remove name from usage & create it which results in a new node on the usage
      u.usage.setName(null);
      usages.create(u);
    } else {
      LOG.debug("Skip usage {} as no name node was created for {}", u.getId(), nn.name.canonicalNameComplete());
    }
    return u.nameNode;
  }
  
  /**
   * Removes the neo4j node with all its relations and all entities stored under this node
   * i.e. NeoUsage and NeoName.
   */
  public void remove(Node n) {
    names().remove(n);
    usages().remove(n);
    removeNodeAndRels(n);
  }

  void removeNodeAndRels(Node n) {
    int counter = 0;
    for (Relationship rel : n.getRelationships()) {
      rel.delete();
      counter++;
    }
    n.delete();
    LOG.debug("Deleted {} from store with {} relations", n, counter);
  }

  Node createNode(PropLabel data) {
    Node n;
    if (isBatchMode()) {
      // batch insert normalizer propLabel used during normalization
      long nodeId = inserter.createNode(data, data.getLabels());
      n = new NodeMock(nodeId);
    } else {
      // create neo4j node and update its propLabel
      n = neo.createNode(data.getLabels());
      NeoDbUtils.addProperties(n, data);
    }
    neoCounter.incrementAndGet();
    return n;
  }
  
  /**
   * Updates a node by adding properties and/or labels
   */
  void updateNode(long nodeId, PropLabel data) {
    if (!data.isEmpty()) {
      if (isBatchMode()) {
        if (data.getLabels() != null) {
          Label[] all = ArrayUtils.addAll(data.getLabels(),
              com.google.common.collect.Iterables.toArray(inserter.getNodeLabels(nodeId), Label.class)
          );
          inserter.setNodeLabels(nodeId, all);
        }
        if (data.size()>0) {
          data.putAll(inserter.getNodeProperties(nodeId));
          inserter.setNodeProperties(nodeId, data);
        }
      } else {
        Node n = neo.getNodeById(nodeId);
        NeoDbUtils.addProperties(n, data);
        NeoDbUtils.addLabels(n, data.getLabels());
      }
    }
  }
  
  /**
   * Updates a node by adding properties and/or labels
   */
  void createRel(Node node1, Node node2, RelationshipType type) {
      if (isBatchMode()) {
        inserter.createRelationship(node1.getId(), node2.getId(), type, null);
      } else {
        node1.createRelationshipTo(node2, type);
      }
  }
  
  /**
   * @return a node which is a dummy proxy only with just an id while we are in batch mode.
   */
  Node nodeById(long nodeId) {
    return isBatchMode() ? new NodeMock(nodeId) : neo.getNodeById(nodeId);
  }
  
  /**
   * Creates or updates a verbatim record.
   * If created a new key is issued.
   */
  public void put(VerbatimRecord v) {
    if (v.hasChanged()) {
      if (v.getKey() == null) {
        v.setKey(verbatimSequence.incrementAndGet());
      }
      verbatim.put(v.getKey(), v);
      v.setHashCode();
    }
  }

  /**
   * Creates a new name relation linking the 2 given nodes.
   * The note and publishedInKey values are stored as relation propLabel
   */
  public void createNameRel(Node n1, Node n2, NeoNameRel rel) {
    Map<String, Object> props = NeoDbUtils.neo4jProps(rel);
    if (isBatchMode()) {
      inserter.createRelationship(n1.getId(), n2.getId(), rel.getType(), props);
    } else {
      Relationship r = n1.createRelationshipTo(n2, rel.getType());
      NeoDbUtils.addProperties(r, props);
    }
  }

  /**
   * Persists a Reference instance, creating a missing id de novo
   */
  public boolean create(Reference r) {
    // create missing id
    if (r.getId() == null) {
      r.setId(idGen.next());
    }
    if (references.containsKey(r.getId())) {
      LOG.warn("Duplicate referenceID {}", r.getId());
      Reference prev = references.get(r.getId());
      addIssues(prev, Issue.ID_NOT_UNIQUE);
      addIssues(r, Issue.ID_NOT_UNIQUE);
      return false;
    }
    
    references.put(r.getId(), r);
    // update lookup index for title
    String normedCit = StringUtils.digitOrAsciiLetters(r.getCitation());
    if (normedCit != null) {
      refIndexCitation.put(normedCit, r.getId());
    }
    return true;
  }

  /**
   * @return the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public VerbatimRecord getVerbatim(int key) {
    VerbatimRecord rec = verbatim.get(key);
    if (rec != null) {
      rec.setHashCode();
    }
    return rec;
  }
  
  /**
   * @return a lazy supplier for the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public Supplier<VerbatimRecord> verbatimSupplier(int key) {
    return new Supplier<VerbatimRecord>() {
      @Override
      public VerbatimRecord get() {
        return getVerbatim(key);
      }
    };
  }
  
  public void addIssues(VerbatimEntity ent, Issue... issue) {
    addIssues(ent.getVerbatimKey(), issue);
  }

  public void addIssues(Integer verbatimKey, Issue... issue) {
    if (verbatimKey != null) {
      VerbatimRecord v = getVerbatim(verbatimKey);
      if (v == null) {
        LOG.warn("No verbatim exists for verbatim key {}", verbatimKey);
      } else {
        for (Issue is : issue) {
          if (is != null) {
            v.addIssue(is);
          }
        }
        put(v);
      }
    }
  }
  
  
  @Override
  public Iterable<Reference> refList() {
    return references.values();
  }

  public Set<String> refIds() {
    return references.keySet();
  }

  public Iterable<VerbatimRecord> verbatimList() {
    return verbatim.values();
  }

  @Override
  public Reference refById(String id) {
    if (id != null) {
      return references.getOrDefault(id, null);
    }
    return null;
  }

  @Override
  public Reference refByCitation(String citation) {
    String normedCit = StringUtils.digitOrAsciiLetters(citation);
    if (normedCit != null && refIndexCitation.containsKey(normedCit)) {
      return references.get(refIndexCitation.get(normedCit));
    }
    return null;
  }

  public Dataset put(Dataset d) {
    // keep existing dataset key & settings
    Dataset old = dataset.get();
    if (old != null) {
      d.setKey(old.getKey());
      d.setCode(old.getCode());
    }
    dataset.set(d);
    return d;
  }

  public Set<Long> nodeIdsOutsideTree() throws InterruptedException {
    final Set<Long> treeNodes = new HashSet<>();
    TreeWalker.walkTree(neo, new StartEndHandler() {
      @Override
      public void start(Node n) {
        treeNodes.add(n.getId());
      }

      @Override
      public void end(Node n) { }
    });

    final Set<Long> nodes = new HashSet<>();
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : neo.getAllNodes()) {
        if (!treeNodes.contains(n.getId())) {
          nodes.add(n.getId());
        }
      }
    }
    return nodes;
  }

  /**
   * overlay neo4j relations to NeoTaxon instances
   */
  private void updateTaxonStoreWithRelations() {
    try (Transaction tx = getNeo().beginTx()) {
      for (Node n : Iterators.loop(getNeo().findNodes(Labels.TAXON))) {
        NeoUsage u = usages().objByNode(n);
        if (u.node.hasLabel(Labels.TAXON) && !u.node.hasLabel(Labels.ROOT)){
          // parent
          Node p = getSingleRelated(u.node, RelType.PARENT_OF, Direction.INCOMING);
          Taxon pt = usages().objByNode(p).getTaxon();
          u.getTaxon().setParentId(pt.getId());
        }
        // store the updated object
        usages().update(u);
      }
      tx.success();
  
      for (Node n : Iterators.loop(getNeo().findNodes(Labels.SYNONYM))) {
        NeoUsage u = usages().objByNode(n);
        if (u.node.hasLabel(Labels.SYNONYM) && !u.isSynonym()) {
          u.convertToSynonym(TaxonomicStatus.SYNONYM);
        }
        // store the updated object
        usages().update(u);
      }
      tx.success();
    }
  }

  /**
   * Sets the same name id for a given cluster of homotypic names derived from name relations and synonym[homotpic=true] relations.
   * We first go thru all synonyms with the homotypic flag to determine the keys and then add all missing basionym nodes.
   */
  private void updateHomotypicNameKeys() {
    int counter = 0;
    LOG.debug("Setting shared homotypic name keys");
    try (Transaction tx = neo.beginTx()) {
      // first homotypic synonym rels
      for (Node syn : Iterators.loop(getNeo().findNodes(Labels.SYNONYM))) {
        NeoName tsyn = nameByUsage(syn);
        if (tsyn.homotypic) {
          Relationship r = syn.getSingleRelationship(RelType.SYNONYM_OF, Direction.OUTGOING);
          if(r == null) {
            addIssues(tsyn, Issue.ACCEPTED_NAME_MISSING);
            continue;
          }
          NeoName acc = nameByUsage(r.getEndNode());
          String homoId;
          if (acc.name.getHomotypicNameId() == null ) {
            homoId = acc.name.getId();
            acc.name.setHomotypicNameId(homoId);
            names().update(acc);
            counter++;
          } else {
            homoId = acc.name.getHomotypicNameId();
          }
          tsyn.name.setHomotypicNameId(homoId);
          names().update(tsyn);
        }
      }
      LOG.info("{} homotypic groups found via homotypic synonym relations", counter);

      // now name relations, reuse keys if existing
      counter = 0;
      for (Node n : Iterators.loop(getNeo().findNodes(Labels.NAME))) {
        // check if this node has a homotypic group already in which case we can skip it
        NeoName start = this.names().objByNode(n);
        if (start.name.getHomotypicNameId() != null) {
          continue;
        }
        // query homotypic group excluding start node
        List<NeoName> group = Traversals.HOMOTYPIC_GROUP
            .traverse(n)
            .nodes()
            .stream()
            .map(this.names()::objByNode)
            .collect(Collectors.toList());
        if (!group.isEmpty()) {
          // we have more than the starting node so we do process, add starting node too
          group.add(start);
          // determine existing or new key to be shared
          String homoId = null;
          for (NeoName t : group) {
            if (t.name.getHomotypicNameId() != null) {
              if (homoId == null) {
                homoId = t.name.getHomotypicNameId();
              } else if (!homoId.equals(t.name.getHomotypicNameId())){
                LOG.warn("Several homotypic name keys found in the same homotypic name group for {}", NeoProperties.getScientificNameWithAuthor(n));
              }
            }
          }
          if (homoId == null) {
            homoId = start.name.getId();
            counter++;
          }
          // update entire group with key
          for (NeoName t : group) {
            if (t.name.getHomotypicNameId() == null) {
              t.name.setHomotypicNameId(homoId);
              names().update(t);
            }
          }
        }
      }
      LOG.info("{} additional homotypic groups found via name relations", counter);
    }
  }

  private Node getSingleRelated(Node n, RelType type, Direction dir) {
    try {
      Relationship rel = n.getSingleRelationship(type, dir);
      if (rel != null) {
        return rel.getOtherNode(n);
      }

    } catch (NotFoundException e) {
      // thrown in case of multiple relations, debug
      LOG.debug("Multiple {} {} relations found for {}: {}", dir, type, n, NeoProperties.getScientificNameWithAuthor(n));
      for (Relationship rel : n.getRelationships(type, dir)) {
        Node other = rel.getOtherNode(n);
        LOG.debug("  {} {}/{}",
            dir == Direction.INCOMING ? "<-- "+type.abbrev+" --" : "-- "+type.abbrev+" -->",
            other, NeoProperties.getScientificNameWithAuthor(other));
      }
      throw new NormalizationFailedException("Multiple "+dir+" "+type+" relations found for "+NeoProperties.getScientificNameWithAuthor(n), e);
    }
    return null;
  }

  /**
   * Sync taxon KVP store with neo4j relations, setting correct neo4j labels, homotypic keys etc
   * Set correct ROOT, PROPARTE and BASIONYM labels for easier access
   */
  public void sync() {
    updateLabels();
    updateTaxonStoreWithRelations();
    updateHomotypicNameKeys();
  }

  /**
   * @return the number of neo4j nodes
   */
  public int size() {
    return neoCounter.get();
  }

  private void updateLabels() {
    // set ROOT
    LOG.debug("Labelling root nodes");
    String query =  "MATCH (r:TAXON) " +
        "WHERE not ( ()-[:PARENT_OF]->(r) ) " +
        "SET r :ROOT " +
        "RETURN count(r)";
    long count = updateLabel(query);
    LOG.info("Labelled {} root nodes", count);

    // set BASIONYM
    LOG.debug("Labelling basionym nodes");
    query = "MATCH (b)<-[:HAS_BASIONYM]-() " +
        "SET b :BASIONYM " +
        "RETURN count(b)";
    count = updateLabel(query);
    LOG.info("Labelled {} basionym nodes", count);
  }

  private long updateLabel(String query) {
    try (Transaction tx = neo.beginTx()) {
      Result result = neo.execute(query);
      tx.success();
      if (result.hasNext()) {
        return (Long) result.next().values().iterator().next();
      } else {
        return 0;
      }
    }
  }

  /**
   * Returns an iterator over all relations of a given type.
   * Requires a valid neo transaction to exist outside of this method call.
   */
  public ResourceIterator<Relationship> iterRelations(RelType type) {
    String query = "MATCH ()-[rel:" + type.name() + "]->() RETURN rel";
    Result result = neo.execute(query);
    return result.columnAs("rel");
  }

  public void assignParent(Node parent, Node child) {
    if (parent != null) {
      if (child.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
        // override existing parent!
        Node oldParent=null;
        for (Relationship r : child.getRelationships(RelType.PARENT_OF, Direction.INCOMING)){
          oldParent = r.getOtherNode(child);
          r.delete();
        }
        LOG.warn("{} has already a parent {}, override with new parent {}",
            NeoProperties.getScientificNameWithAuthor(child),
            NeoProperties.getScientificNameWithAuthor(oldParent),
            NeoProperties.getScientificNameWithAuthor(parent));

      } else {
        parent.createRelationshipTo(child, RelType.PARENT_OF);
      }

    }
  }

  /**
   * Creates a synonym relationship between the given synonym and the accepted node, updating labels accordingly
   * and also moving potentially existing parent_of relations.
   * Homotypic relation flag is not set and expected to be added if known to be homotypic.
   *
   * @return newly created synonym relation
   */
  public Relationship createSynonymRel(Node synonym, Node accepted) {
    Relationship synRel = synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
    synonym.addLabel(Labels.SYNONYM);
    synonym.removeLabel(Labels.TAXON);
    // potentially move the parent relationship of the synonym
    if (synonym.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
      try {
        Relationship rel = synonym.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING);
        if (rel != null) {
          // check if accepted has a parent relation already
          if (!accepted.hasRelationship(RelType.PARENT_OF, Direction.INCOMING)) {
            assignParent(rel.getStartNode(), accepted);
          }
        }
      } catch (RuntimeException e) {
        // more than one parent relationship exists, should never be the case, sth wrong!
        LOG.error("Synonym {} has multiple parent relationships!", synonym.getId());
        //for (Relationship r : synonym.getRelationships(RelType.PARENT_OF)) {
        //  r.delete();
        //}
      }
    }
    return synRel;
  }

  /**
   * Get the name object for a usage via its HasName relation.
   */
  public NeoName nameByUsage(final Node usage) {
    return names().objByNode(
            usage.getSingleRelationship(RelType.HAS_NAME, Direction.OUTGOING).getOtherNode(usage)
    );
  }
  
  /**
   * Get the name object for a usage via its HasName relation.
   */
  public List<NeoUsage> usagesByName(final Node nameNode) {
    return Iterables.stream(nameNode.getRelationships(RelType.HAS_NAME, Direction.INCOMING))
        .map(rel -> usages().objByNode(rel.getOtherNode(nameNode)))
        .collect(Collectors.toList());
  }
  
  /**
   * Get the name object for a usage via its HasName relation.
   */
  public List<Node> usageNodesByName(final Node nameNode) {
    List<Node> usages = new ArrayList<>();
    nameNode.getRelationships(RelType.HAS_NAME, Direction.INCOMING).forEach(
        un -> usages.add(un.getOtherNode(nameNode))
    );
    return usages;
  }
  
  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<RankedName> accepted(Node synonym) {
    return Traversals.ACCEPTED.traverse(synonym).nodes().stream()
        .map(NeoProperties::getRankedName)
        .collect(Collectors.toList());
  }
  
  /**
   * List all accepted taxa of a potentially prop parte synonym
   */
  public List<RankedName> parents(Node child) {
    return Traversals.PARENTS.traverse(child).nodes().stream()
        .map(NeoProperties::getRankedName)
        .collect(Collectors.toList());
  }

  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification propLabel.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can getUsage resolved into proper neo relations later.
   * Name and taxon ids are generated de novo.
   *
   * @param name the new name to be used
   * @param source the taxon source to copy from
   * @param excludeRankAndBelow the rank (and all ranks below) to exclude from the source classification
   */
  public RankedName createDoubtfulFromSource(Origin origin,
                                             Name name,
                                             @Nullable NeoUsage source,
                                             Rank excludeRankAndBelow) {
    NeoUsage u = NeoUsage.createTaxon(origin, name, true);
    // copy verbatim classification from source
    if (source != null) {
      if (source.classification != null) {
        u.classification = Classification.copy(source.classification);
        // remove lower ranks
        u.classification.clearRankAndBelow(excludeRankAndBelow);
      }
      // copy parent props from source
      if (u.getVerbatimKey() != null) {
        VerbatimRecord sourceTerms = getVerbatim(u.getVerbatimKey());
        VerbatimRecord copyTerms = new VerbatimRecord();
        copyTerms.put(DwcTerm.parentNameUsageID, sourceTerms.get(DwcTerm.parentNameUsageID));
        copyTerms.put(DwcTerm.parentNameUsage, sourceTerms.get(DwcTerm.parentNameUsage));
        put(copyTerms);
        u.setVerbatimKey(copyTerms.getKey());
      }
    }

    // store, which creates a new neo node
    createNameAndUsage(u);

    return new RankedName(u.node, name.getScientificName(), name.authorshipComplete(), name.getRank());
  }

  public void updateIdGeneratorPrefix() {
    idGen.setPrefix(
        Stream.concat(
            refIds().stream(),
            Stream.concat(
                usages().allIds(),
                names().allIds()
            )
        )
    );
    if (idGen.getCounter() > 0) {
      // TODO: update references, anything else should have source ids at this point

    }
    LOG.info("ID generator updated with unique prefix {}", idGen.getPrefix());
  }
  
  public void reportDuplicates() {
    if (names().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate nameIds! Only the first record will be used", names().getDuplicateCounter());
    }
    if (usages().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate taxonIds! Only the first record will be used", usages().getDuplicateCounter());
    }
  }
  
}


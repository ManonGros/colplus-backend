package org.col.importer;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Users;
import org.col.common.lang.InterruptedRuntimeException;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.config.ImporterConfig;
import org.col.dao.Partitioner;
import org.col.db.mapper.*;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbUtils;
import org.col.importer.neo.model.Labels;
import org.col.importer.neo.model.NeoName;
import org.col.importer.neo.model.NeoUsage;
import org.col.importer.neo.model.RelType;
import org.col.importer.neo.traverse.StartEndHandler;
import org.col.importer.neo.traverse.TreeWalker;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.lang.Exceptions.interruptIfCancelled;

/**
 *
 */
public class PgImport implements Callable<Boolean> {
  private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);
  
  private final NeoDb store;
  private final int batchSize;
  private final SqlSessionFactory sessionFactory;
  private final AuthorshipNormalizer aNormalizer;
  private final Dataset dataset;
  private final BiMap<Integer, Integer> verbatimKeys = HashBiMap.create();
  private final Set<String> proParteIds = new HashSet<>();
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger sCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger diCounter = new AtomicInteger(0);
  private final AtomicInteger deCounter = new AtomicInteger(0);
  private final AtomicInteger mCounter = new AtomicInteger(0);
  private final AtomicInteger vCounter = new AtomicInteger(0);
  
  public PgImport(int datasetKey, NeoDb store, SqlSessionFactory sessionFactory, AuthorshipNormalizer aNormalizer,
                  ImporterConfig cfg) {
    this.dataset = store.getDataset();
    this.dataset.setKey(datasetKey);
    this.aNormalizer = aNormalizer;
    this.store = store;
    this.batchSize = cfg.batchSize;
    this.sessionFactory = sessionFactory;
  }
  
  @Override
  public Boolean call() throws InterruptedException, InterruptedRuntimeException {
    Partitioner.partition(sessionFactory, dataset.getKey());
    
    insertVerbatim();
    
    insertReferences();
    
    insertNames();
    
    insertNameRelations();
    
		insertUsages();
  
    Partitioner.indexAndAttach(sessionFactory, dataset.getKey());
    
    updateMetadata();
		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} synonyms, {} references, {} vernaculars, {} distributions, {} descriptions and {} media items",
        dataset.getKey(), verbatimKeys.size(),
        nCounter, tCounter, sCounter, rCounter, vCounter, diCounter, deCounter, mCounter);
		return true;
	}
  
  private void updateMetadata() {
    try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      Dataset old = mapper.get(dataset.getKey());
      copyIfNotNull(dataset::getAlias, old::setAlias);
      copyIfNotNull(dataset::getAuthorsAndEditors, old::setAuthorsAndEditors);
      copyIfNotNull(dataset::getCompleteness, old::setCompleteness);
      copyIfNotNull(dataset::getConfidence, old::setConfidence);
      copyIfNotNull(dataset::getContact, old::setContact);
      copyIfNotNull(dataset::getDescription, old::setDescription);
      copyIfNotNull(dataset::getGroup, old::setGroup);
      copyIfNotNull(dataset::getLicense, old::setLicense);
      copyIfNotNull(dataset::getOrganisations, old::setOrganisations);
      copyIfNotNull(dataset::getReleased, old::setReleased);
      copyIfNotNull(dataset::getTitle, old::setTitle);
      copyIfNotNull(dataset::getType, old::setType);
      copyIfNotNull(dataset::getVersion, old::setVersion);
      copyIfNotNull(dataset::getWebsite, old::setWebsite);
      
      mapper.update(old);
      session.commit();
    }
  }
  
  private <T> void copyIfNotNull(Supplier<T> getter, Consumer<T> setter) {
    T val = getter.get();
    if (val != null) {
      setter.accept(val);
    }
  }
  
  private void insertVerbatim() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
      int counter = 0;
      Map<Integer, VerbatimRecord> batchCache = new HashMap<>();
      for (VerbatimRecord v : store.verbatimList()) {
        int storeKey = v.getId();
        v.setId(null);
        v.setDatasetKey(dataset.getKey());
        mapper.create(v);
        batchCache.put(storeKey, v);
        if (++counter % batchSize == 0) {
          commitVerbatimBatch(session, batchCache);
          LOG.debug("Inserted {} verbatim records so far", counter);
        }
      }
      commitVerbatimBatch(session, batchCache);
      LOG.info("Inserted {} verbatim records", counter);
    }
  }
  
  private void commitVerbatimBatch(SqlSession session, Map<Integer, VerbatimRecord> batchCache) {
    interruptIfCancelled();
    session.commit();
    // we only get the new keys after we committed in batch mode!!!
    for (Map.Entry<Integer, VerbatimRecord> e : batchCache.entrySet()) {
      verbatimKeys.put(e.getKey(), e.getValue().getId());
    }
    batchCache.clear();
  }
  
  private <T extends VerbatimEntity & UserManaged & DatasetScoped> T updateVerbatimUserEntity(T ent) {
    ent.setDatasetKey(dataset.getKey());
    return updateUser(updateVerbatimEntity(ent));
  }
  
  private <T extends VerbatimEntity> T updateVerbatimEntity(T ent) {
    if (ent != null && ent.getVerbatimKey() != null) {
      ent.setVerbatimKey(verbatimKeys.get(ent.getVerbatimKey()));
    }
    return ent;
  }

  private static <T extends UserManaged> T updateUser(T ent) {
    if (ent != null) {
      ent.setCreatedBy(Users.IMPORTER);
      ent.setModifiedBy(Users.IMPORTER);
    }
    return ent;
  }

  private void insertReferences() throws InterruptedException {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        r.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(r);
        updateUser(r);
        mapper.create(r);
        rCounter.incrementAndGet();
        if (counter++ % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} references", counter);
        }
      }
      session.commit();
      LOG.debug("Inserted all {} references", counter);
    }
  }
  
  
  /**
   * Inserts all names, collecting all homotypic name keys for later updates if they havent been inserted already.
   */
  private void insertNames() {
    try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.names().all().forEach(n -> {
        n.name.setDatasetKey(dataset.getKey());
        updateVerbatimUserEntity(n.name);
        // normalize authorship on insert - sth the DAO normally does but we use the mapper directly in batch mode
        n.name.setAuthorshipNormalized(aNormalizer.normalizeName(n.name));
        nameMapper.create(n.name);
        if (nCounter.incrementAndGet() % batchSize == 0) {
          interruptIfCancelled();
          session.commit();
          LOG.debug("Inserted {} other names", nCounter.get());
        }
      });
      session.commit();
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }
  
  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertNameRelations() {
    for (RelType rt : RelType.values()) {
      if (!rt.isNameRel()) continue;

      final AtomicInteger counter = new AtomicInteger(0);
      try (final SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
        final NameRelationMapper nameRelationMapper = session.getMapper(NameRelationMapper.class);
        LOG.debug("Inserting all {} relations", rt);
        try (Transaction tx = store.getNeo().beginTx()) {
          store.iterRelations(rt).stream().forEach(rel -> {
            NameRelation nr = store.toRelation(rel);
            nameRelationMapper.create(updateUser(nr));
            if (counter.incrementAndGet() % batchSize == 0) {
              interruptIfCancelled();
              session.commit();
            }
          });
        }
        session.commit();
      }
      LOG.info("Inserted {} {} relations", counter.get(), rt);
    }
  }

	/**
	 * insert taxa/synonyms with all the rest
	 */
	private void insertUsages() throws InterruptedException {
		try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH,false)) {
      LOG.info("Inserting remaining names and all taxa");
      DescriptionMapper descriptionMapper = session.getMapper(DescriptionMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      MediaMapper mediaMapper = session.getMapper(MediaMapper.class);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte synonyms will be visited multiple times, remember their name ids!
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<String> parentIds = new Stack<>();
        
        @Override
        public void start(Node n) {
          NeoUsage u = store.usages().objByNode(n);
          NeoName nn = store.nameByUsage(n);
          updateVerbatimEntity(u);
          updateVerbatimEntity(nn);

          // update share props for taxon or synonym
          NameUsageBase nu = u.usage;
          nu.setName(nn.name);
          nu.setDatasetKey(dataset.getKey());
          updateUser(nu);
          if (!parentIds.empty()) {
            // use parent postgres key from stack, but keep it there
            nu.setParentId(parentIds.peek());
          } else if (u.isSynonym()) {
            throw new IllegalStateException("Synonym node " + n.getId() + " without accepted taxon found: " + nn.name.getScientificName());
          } else if (!n.hasLabel(Labels.ROOT)) {
            throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + nn.name.getScientificName());
          }
  
          // insert taxon or synonym
          if (u.isSynonym()) {
            if (NeoDbUtils.isProParteSynonym(n)) {
              if (proParteIds.contains(u.getId())){
                // we had that id before, append a random suffix for further pro parte usage
                UUID ppID = UUID.randomUUID();
                u.setId(u.getId() + "-" + ppID);
              } else {
                proParteIds.add(u.getId());
              }
            }
            synMapper.create(u.getSynonym());
            sCounter.incrementAndGet();

          } else {
            taxonMapper.create(updateUser(u.getTaxon()));
            tCounter.incrementAndGet();
            Taxon acc = u.getTaxon();

            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentIds.push(acc.getId());
            
            // insert vernacular
            for (VernacularName vn : u.vernacularNames) {
              updateVerbatimUserEntity(vn);
              vernacularMapper.create(vn, acc.getId());
              vCounter.incrementAndGet();
            }
            
            // insert distributions
            for (Distribution d : u.distributions) {
              updateVerbatimUserEntity(d);
              distributionMapper.create(d, acc.getId());
              diCounter.incrementAndGet();
            }
  
            // insert descriptions
            for (Description d : u.descriptions) {
              updateVerbatimUserEntity(d);
              descriptionMapper.create(d, acc.getId());
              deCounter.incrementAndGet();
            }
  
            // insert media
            for (Media m : u.media) {
              updateVerbatimUserEntity(m);
              mediaMapper.create(m, acc.getId());
              mCounter.incrementAndGet();
            }
            
          }

          // commit in batches
          if (counter++ % batchSize == 0) {
            interruptIfCancelled();
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }
        
        @Override
        public void end(Node n) {
          interruptIfCancelled();
          // remove this key from parent queue if its an accepted taxon
          if (n.hasLabel(Labels.TAXON)) {
            parentIds.pop();
          }
        }
      });
      session.commit();
      LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);
    }
  }
  
}

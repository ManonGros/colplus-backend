package org.col.admin.task.importer;

import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import jersey.repackaged.com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.ImporterConfig;
import org.col.admin.task.importer.neo.NeoDb;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.RelType;
import org.col.admin.task.importer.neo.traverse.StartEndHandler;
import org.col.admin.task.importer.neo.traverse.TreeWalker;
import org.col.api.model.*;
import org.col.api.vocab.NomActType;
import org.col.api.vocab.Origin;
import org.col.db.dao.NameDao;
import org.col.db.mapper.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class PgImport implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(PgImport.class);

	private final NeoDb store;
	private final int batchSize;
	private final SqlSessionFactory sessionFactory;
	private final Dataset dataset;
	private Map<Integer, Integer> nameKeys = Maps.newHashMap();
  private Map<Integer, Integer> referenceKeys = Maps.newHashMap();
  private final AtomicInteger verbatimCounter = new AtomicInteger(0);
  private final AtomicInteger nCounter = new AtomicInteger(0);
  private final AtomicInteger tCounter = new AtomicInteger(0);
  private final AtomicInteger rCounter = new AtomicInteger(0);
  private final AtomicInteger dCounter = new AtomicInteger(0);
  private final AtomicInteger vCounter = new AtomicInteger(0);

	public PgImport(int datasetKey, NeoDb store, SqlSessionFactory sessionFactory,
                  ImporterConfig cfg) {
		this.dataset = store.getDataset();
		this.dataset.setKey(datasetKey);
		this.store = store;
		this.batchSize = cfg.batchSize;
		this.sessionFactory = sessionFactory;
	}

	@Override
	public void run() {
	  truncate();
		insertReferences();
    insertNames();
    insertActs();
		insertTaxa();

		updateMetadata();

		LOG.info("Completed dataset {} insert with {} verbatim records, " +
        "{} names, {} taxa, {} references, {} vernaculars and {} distributions",
        dataset.getKey(), verbatimCounter,
        nCounter, tCounter, rCounter, vCounter, dCounter);
	}

  private void truncate(){
    try (SqlSession session = sessionFactory.openSession(true)) {
      LOG.info("Remove existing data for dataset {}: {}", dataset.getKey(), dataset.getTitle());
      DatasetMapper mapper = session.getMapper(DatasetMapper.class);
      mapper.truncateDatasetData(dataset.getKey());
      session.commit();
    }
  }

	private void updateMetadata() {
		try (SqlSession session = sessionFactory.openSession(false)) {
			LOG.info("Updating dataset metadata for {}: {}", dataset.getKey(), dataset.getTitle());
			DatasetMapper mapper = session.getMapper(DatasetMapper.class);
			Dataset old = mapper.get(dataset.getKey());
			if (dataset.getTitle() != null) {
			  // make sure we keep a title even if old
        old.setTitle(dataset.getTitle());
			}
      old.setAuthorsAndEditors(dataset.getAuthorsAndEditors());
      old.setContactPerson(dataset.getContactPerson());
      old.setDescription(dataset.getDescription());
      old.setHomepage(dataset.getHomepage());
      old.setLicense(dataset.getLicense());
      old.setOrganisation(dataset.getOrganisation());
      old.setReleaseDate(dataset.getReleaseDate());
      old.setVersion(dataset.getVersion());

			mapper.update(old);
			session.commit();
		}
	}

	private void insertReferences() {
    try (final SqlSession session = sessionFactory.openSession(false)) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      int counter = 0;
      for (Reference r : store.refList()) {
        int storeKey = r.getKey();
        r.setDatasetKey(dataset.getKey());
        mapper.create(r);
        rCounter.incrementAndGet();
        // store mapping of key used in the store to the key used in postgres
        referenceKeys.put(storeKey, r.getKey());
        if (counter++ % batchSize == 0) {
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
    // key=postgres name key, value=desired homotypic name key using the temp neo4j node
    Map<Integer, Integer> nameHomoKey = Maps.newHashMap();
    try (final SqlSession session = sessionFactory.openSession(false)) {
      final NameMapper nameMapper = session.getMapper(NameMapper.class);
      LOG.debug("Inserting all names");
      store.process(Labels.ALL, batchSize, new NeoDb.NodeBatchProcessor() {
        @Override
        public void process(Node n) {
          // we read all names as we also deal with acts for basionyms here
          NeoTaxon t = store.get(n);
          Integer homoKey = null;
          if (t.name.getHomotypicNameKey() != null) {
            if (t.name.getHomotypicNameKey() == n.getId()) {
              // pointer to itself, remove the key as the mapper expects a null in such case
              t.name.setHomotypicNameKey(null);
            } else if (nameKeys.containsKey(t.name.getHomotypicNameKey())) {
              // update homotypic key directly
              t.name.setHomotypicNameKey(nameKeys.get(t.name.getHomotypicNameKey()));
            } else {
              // queue for later updates
              homoKey = t.name.getHomotypicNameKey();
              t.name.setHomotypicNameKey(null);
            }
          }
          // update published in reference keys
          if (t.name.getPublishedInKey() != null) {
            t.name.setPublishedInKey(referenceKeys.get(t.name.getPublishedInKey()));
          }

          t.name.setDatasetKey(dataset.getKey());
          t.name.getIssues().addAll(t.taxon.getIssues());

          nameMapper.create(t.name);
          nCounter.incrementAndGet();
          // keep postgres keys in node id map
          nameKeys.put((int) t.node.getId(), t.name.getKey());

          if (homoKey != null) {
            nameHomoKey.put(t.name.getKey(), homoKey);
          }
        }

        @Override
        public void commitBatch(int counter) {
          session.commit();
          LOG.debug("Inserted {} other names", counter);
        }
      });
      session.commit();

      int homoUpdateCounter = 0;
      for (Map.Entry<Integer, Integer> homo : nameHomoKey.entrySet()) {
        nameMapper.updateHomotypicNameKey(homo.getKey(), nameKeys.get(homo.getValue()));
        homoUpdateCounter++;
        if (homoUpdateCounter % 1000 == 0) {
          session.commit();
        }
      }
      session.commit();
      LOG.info("Updated homotypic name key of {} names", homoUpdateCounter);
    }
    LOG.info("Inserted {} name in total", nCounter.get());
  }

  /**
   * Go through all neo4j relations and convert them to name acts if the rel type matches
   */
  private void insertActs() {
    // neo4j relationship types need to be compared by their name!
    final Map<String, NomActType> actTypes = ImmutableMap.<String, NomActType>builder()
        .put(RelType.BASIONYM_OF.name(), NomActType.BASIONYM)
        .build();
    final Set<NomActType> inverse = Sets.newHashSet(NomActType.BASIONYM);
    final AtomicInteger counter = new AtomicInteger(0);
    try (final SqlSession session = sessionFactory.openSession(false)) {
      final NameActMapper nameActMapper = session.getMapper(NameActMapper.class);
      LOG.debug("Inserting all name acts");
      try (Transaction tx = store.getNeo().beginTx()) {
        store.getNeo().getAllRelationships().stream().forEach(rel -> {
          if (actTypes.containsKey(rel.getType().name())) {
            NomActType actType = actTypes.get(rel.getType().name());
            Node n1 = rel.getStartNode();
            Node n2 = rel.getEndNode();
            Node from = inverse.contains(actType) ? rel.getEndNode() : rel.getStartNode();

            NameAct act = new NameAct();
            act.setDatasetKey(dataset.getKey());
            act.setType(actType);
            act.setNameKey(nameKeys.get((int) from.getId()));
            act.setRelatedNameKey(nameKeys.get((int) rel.getOtherNode(from).getId()));
            //TODO: read note from rel property
            act.setNote(null);
            nameActMapper.create(act);
            if (counter.incrementAndGet() % 1000 == 0) {
              session.commit();
            }
          }
        });
      }
      session.commit();
    }
    LOG.info("Inserted {} name acts", counter.get());
  }

  private int createTaxon(TaxonMapper mapper, NeoTaxon t) {
    t.taxon.setDatasetKey(dataset.getKey());
    t.taxon.setName(t.name);
    mapper.create(t.taxon);
    tCounter.incrementAndGet();
    return t.taxon.getKey();
  }

	/**
	 * insert taxa with all the rest
	 */
	private void insertTaxa() {
		try (SqlSession session = sessionFactory.openSession(false)) {
      LOG.info("Inserting remaining names and all taxa");
      NameDao nameDao = new NameDao(session);
      TaxonMapper taxonMapper = session.getMapper(TaxonMapper.class);
      VerbatimRecordMapper verbatimMapper = session.getMapper(VerbatimRecordMapper.class);
      DistributionMapper distributionMapper = session.getMapper(DistributionMapper.class);
      VernacularNameMapper vernacularMapper = session.getMapper(VernacularNameMapper.class);
      ReferenceMapper refMapper = session.getMapper(ReferenceMapper.class);

      // iterate over taxonomic tree in depth first order, keeping postgres parent keys
      // pro parte listByTaxon will be visited multiple times, remember their name pg key!
      Long2IntMap proParteNames = new Long2IntOpenHashMap();
      TreeWalker.walkTree(store.getNeo(), new StartEndHandler() {
        int counter = 0;
        Stack<Integer> parentKeys = new Stack<Integer>();

        @Override
        public void start(Node n) {
          NeoTaxon t = store.get(n);
          // use postgres name key
          t.name.setKey(nameKeys.get((int) n.getId()));
          // is this a pro parte synonym that we have processed before already?
          if (proParteNames.containsKey(n.getId())) {
            // now add another synonym relation now that the other accepted exists in pg
            nameDao.addSynonym(dataset.getKey(), proParteNames.get(n.getId()), parentKeys.peek(), t.synonym.getStatus(), t.synonym.getAccordingTo());
            return;
          }

          // insert accepted taxon or synonym
          Integer taxonKey;
          if (t.isSynonym()) {
            taxonKey = null;
            nameDao.addSynonym(dataset.getKey(), t.name.getKey(), parentKeys.peek(), t.synonym.getStatus(), t.synonym.getAccordingTo());
            if (!t.distributions.isEmpty()) {
              LOG.debug("Distributions found for synonym {}: {}, ignore", t.name.getKey(), t.name);
            }
            if (!t.vernacularNames.isEmpty()) {
              LOG.debug("Vernacular names found for synonym {}: {}, ignore", t.name.getKey(), t.name);
            }
            if (!t.bibliography.isEmpty()) {
              LOG.debug("Bibliography found for synonym {}: {}, ignore", t.name.getKey(), t.name);
            }

          } else {
            if (!parentKeys.empty()) {
              // use parent postgres key from stack, but keep it there
              t.taxon.setParentKey(parentKeys.peek());
            } else if (!n.hasLabel(Labels.ROOT)) {
              throw new IllegalStateException("Non root node " + n.getId() + " with an accepted taxon without parent found: " + t.name.getScientificName());
            }
            taxonKey = createTaxon(taxonMapper, t);
            // push new postgres key onto stack for this taxon as we traverse in depth first
            parentKeys.push(taxonKey);

            // insert vernacular
            for (VernacularName vn : t.vernacularNames) {
              updateRefKeys(vn);
              vernacularMapper.create(vn, taxonKey, dataset.getKey());
              vCounter.incrementAndGet();
            }

            // insert distributions
            for (Distribution d : t.distributions) {
              updateRefKeys(d);
              distributionMapper.create(d, taxonKey, dataset.getKey());
              dCounter.incrementAndGet();
            }

            // link bibliography
            for (Integer refKey : t.bibliography) {
              refMapper.linkToTaxon(dataset.getKey(), taxonKey, referenceKeys.get(refKey));
            }
          }

          // insert verbatim rec
          LOG.debug("verbatim {}{} tax={} name={}:{}",
              t.name.getOrigin(),
              t.verbatim==null? "" : " "+t.verbatim.getId(),
              taxonKey,
              t.name.getKey(),
              t.name.canonicalNameComplete()
          );
          if (t.name.getOrigin().equals(Origin.SOURCE)) {
            t.verbatim.setDatasetKey(dataset.getKey());
            verbatimMapper.create(t.verbatim, taxonKey, t.name.getKey(), null);
            verbatimCounter.incrementAndGet();
          }

          // commit in batches
          if (counter++ % batchSize == 0) {
            session.commit();
            LOG.info("Inserted {} names and taxa", counter);
          }
        }

        /**
         * Updates reference keys from internal store keys to postgres keys
         */
        private void updateRefKeys(Referenced obj) {
          obj.setReferenceKeys(
              obj.getReferenceKeys().stream()
                  .map(referenceKeys::get)
                  .collect(Collectors.toSet())
          );
        }

        @Override
        public void end(Node n) {
          // remove this key from parent list if its an accepted taxon
          if (n.hasLabel(Labels.TAXON)) {
            parentKeys.pop();
          }
        }
      });
      session.commit();
      LOG.debug("Inserted {} names and {} taxa", nCounter, tCounter);

		} catch (Exception e) {
      LOG.error("Fatal error during names and taxa insert for dataset {}", dataset.getKey(), e);
      throw e;
		}
	}

}

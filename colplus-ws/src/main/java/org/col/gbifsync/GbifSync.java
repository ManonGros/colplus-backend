package org.col.gbifsync;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.dropwizard.lifecycle.Managed;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.vocab.Users;
import org.col.config.GbifConfig;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.common.concurrent.ExecutorUtils;
import org.col.common.util.LoggingUtils;
import org.col.db.mapper.DatasetMapper;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSync implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSync.class);
  private static final String THREAD_NAME = "gbif-sync";
  public static final UUID PLAZI_KEY = UUID.fromString("7ce8aef0-9e92-11dc-8738-b8a03c50a862");
  
  private ScheduledExecutorService scheduler;
  private GbifSyncJob job;
  private final GbifConfig cfg;
  private final SqlSessionFactory sessionFactory;
  private final RxClient<RxCompletionStageInvoker> rxClient;
  
  public GbifSync(GbifConfig gbif, SqlSessionFactory sessionFactory, RxClient<RxCompletionStageInvoker> rxClient) {
    this.cfg = gbif;
    this.sessionFactory = sessionFactory;
    this.rxClient = rxClient;
  }
  
  static class GbifSyncJob implements Runnable {
    private final RxClient<RxCompletionStageInvoker> rxClient;
    private final SqlSessionFactory sessionFactory;
    private final GbifConfig gbif;
    private int created;
    private int updated;
    private int deleted;
    private DatasetMapper mapper;
    private DatasetPager pager;
    
    public GbifSyncJob(GbifConfig gbif, RxClient<RxCompletionStageInvoker> rxClient, SqlSessionFactory sessionFactory) {
      this.gbif = gbif;
      this.rxClient = rxClient;
      this.sessionFactory = sessionFactory;
    }
    
    @Override
    public void run() {
      MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());
      try (SqlSession session = sessionFactory.openSession(true)) {
        pager = new DatasetPager(rxClient, gbif);
        mapper = session.getMapper(DatasetMapper.class);
        if (gbif.insert) {
          syncAll();
        } else {
          updateExisting();
        }
        session.commit();
        LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);
        
      } catch (Exception e) {
        LOG.error("Failed to sync with GBIF", e);
      }
      MDC.remove(LoggingUtils.MDC_KEY_TASK);
    }
    
    
    private void syncAll() throws Exception {
      LOG.info("Syncing all datasets from GBIF registry {}", gbif.api);
      while (pager.hasNext()) {
        List<Dataset> page = pager.next();
        LOG.debug("Received page " + pager.currPageNumber() + " with " + page.size() + " datasets from GBIF");
        for (Dataset gbif : page) {
          sync(gbif, mapper.getByGBIF(gbif.getGbifKey()));
        }
      }
      //TODO: delete datasets no longer in GBIF
    }
    
    private void updateExisting() throws Exception {
      LOG.info("Syncing existing datasets with GBIF registry {}", gbif.api);
      Page page = new Page(100);
      List<Dataset> datasets = null;
      while (datasets == null || !datasets.isEmpty()) {
        datasets = mapper.list(page);
        for (Dataset d : datasets) {
          if (d.getGbifKey() != null) {
            Dataset gbif = pager.get(d.getGbifKey());
            sync(gbif, d);
          }
        }
        page.next();
      }
    }
    
    private void sync(Dataset gbif, Dataset curr) throws Exception {
      if (curr == null) {
        // create new dataset
        gbif.setCreatedBy(Users.GBIF_SYNC);
        gbif.setModifiedBy(Users.GBIF_SYNC);
        mapper.create(gbif);
        created++;
        LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());
        
      } else if (!Objects.equals(gbif.getDataAccess(), curr.getDataAccess()) ||
          !Objects.equals(gbif.getLicense(), curr.getLicense()) ||
          !Objects.equals(gbif.getOrganisations(), curr.getOrganisations()) ||
          !Objects.equals(gbif.getWebsite(), curr.getWebsite())
          ) {
        //we modify core metadata (title, description, contacts, version) via the dwc archive metadata
        //gbif syncs only change one of the following
        // - dwca access url
        // - license
        // - organization (publisher)
        // - homepage
        curr.setDataAccess(gbif.getDataAccess());
        curr.setLicense(gbif.getLicense());
        curr.setOrganisations(gbif.getOrganisations());
        curr.setWebsite(gbif.getWebsite());
        mapper.update(curr);
        updated++;
      }
    }
  }
  
  public boolean isActive() {
    return job != null;
  }
  
  public void syncNow() {
    Runnable job = new GbifSyncJob(cfg, rxClient, sessionFactory);
    job.run();
  }
  
  @Override
  public void start() throws Exception {
    if (cfg.syncFrequency > 0) {
      scheduler = Executors.newScheduledThreadPool(1,
          new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
      );
      LOG.info("Enable GBIF registry sync job every {} hours", cfg.syncFrequency);
      job = new GbifSyncJob(cfg, rxClient, sessionFactory);
      scheduler.scheduleAtFixedRate(job, 0, cfg.syncFrequency, TimeUnit.HOURS);
   
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
  }
  
  @Override
  public void stop() throws Exception {
    if (scheduler != null) {
      ExecutorUtils.shutdown(scheduler, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
    }
    job = null;
  }
}

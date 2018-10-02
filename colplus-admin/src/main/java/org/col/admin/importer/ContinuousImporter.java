package org.col.admin.importer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.ImporterConfig;
import org.col.api.model.Dataset;
import org.col.common.util.LoggingUtils;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.dropwizard.lifecycle.Managed;

import static org.col.admin.AdminServer.MILLIS_TO_DIE;

/**
 * A scheduler for new import jobs that runs continuously in the background
 * and submits new import jobs to the ImportManager if it is idle.
 *
 * New jobs are selected by priority according to the following criteria:
 *
 *  - never imported datasets first
 *  - the datasets configured indexing frequency
 */
public class ContinuousImporter implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ContinuousImporter.class);
  private static final String THREAD_NAME = "continuous-importer";
  private static final int MIN_SIZE = 10;
  private static final int BATCH_SIZE = 50;
  private static final int WAIT_TIME_IN_HOURS = 1;

  private Thread thread;
  private final ContinousImporterJob job;

  public ContinuousImporter(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
    this.job = new ContinousImporterJob(cfg, manager, factory);
    if (cfg.maxQueue < BATCH_SIZE) {
      job.running=false;
      LOG.warn("Importer queue is shorter ({}) than the amount of batches ({}) to submit. Shutdown continuous importer!", cfg.maxQueue, BATCH_SIZE);
    }
  }

  static class ContinousImporterJob implements Runnable {
    private final SqlSessionFactory factory;
    private final ImportManager manager;
    private final ImporterConfig cfg;
    private volatile boolean running = true;

    public ContinousImporterJob(ImporterConfig cfg, ImportManager manager, SqlSessionFactory factory) {
      this.manager = manager;
      this.factory = factory;
      this.cfg = cfg;
    }

    public void terminate() {
      running = false;
    }

    @Override
    public void run() {
      MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());

      while (running) {
        try {
          while (manager.list().size() > MIN_SIZE) {
            LOG.debug("Importer busy, sleep for {} minutes", cfg.continousImportPolling);
            TimeUnit.MINUTES.sleep(cfg.continousImportPolling);
          }
          List<Dataset> datasets = fetch();
          if (datasets.isEmpty()) {
            LOG.info("No datasets eligable to be imported. Sleep for {} hour", WAIT_TIME_IN_HOURS);
            TimeUnit.HOURS.sleep(WAIT_TIME_IN_HOURS);

          } else {
            for (Dataset d : datasets) {
              try {
                manager.submit(d.getKey(), false);
              } catch (IllegalArgumentException e) {
                // ignore
              }
            }
          }
        } catch (InterruptedException e) {
          LOG.info("Interrupted continuous importing. Stop");
          running = false;

        } catch (Exception e) {
          LOG.error("Error scheduling continuous imports. Shutdown continous importer!", e);
          running = false;
        }
      }
      MDC.remove(LoggingUtils.MDC_KEY_TASK);
    }

    /**
     * Find the next batch of datasets eligable for importing
     */
    private List<Dataset> fetch() {
      // check never crawled datasets first
      List<Dataset> datasets;
      try (SqlSession session = factory.openSession(true)){
        datasets = session.getMapper(DatasetMapper.class).listNeverImported(BATCH_SIZE);
        if (datasets.isEmpty()) {
          // now check for eligable datasets based on import frequency
          datasets = session.getMapper(DatasetMapper.class).listToBeImported(BATCH_SIZE);
        }
      }
      return datasets;
    }
  }

  @Override
  public void start() throws Exception {
    thread = new Thread(job, THREAD_NAME);
    LOG.info("Start continuous importing with maxQueue={}, polling every {} minutes",
        job.cfg.maxQueue, job.cfg.continousImportPolling
    );
    thread.start();
  }

  @Override
  public void stop() throws Exception {
    job.terminate();
    thread.join(MILLIS_TO_DIE);
  }
}

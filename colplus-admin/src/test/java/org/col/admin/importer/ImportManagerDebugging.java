package org.col.admin.importer;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.Files;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.admin.matching.NameIndexFactory;
import org.col.db.PgSetupRule;
import org.col.es.EsClientFactory;
import org.elasticsearch.client.RestClient;
import org.junit.*;

@Ignore("manual import debugging")
public class ImportManagerDebugging {

  ImportManager importManager;
  CloseableHttpClient hc;
  RestClient esClient;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  private AdminServerConfig provideConfig() {
    AdminServerConfig cfg = new AdminServerConfig();
    cfg.gbif.syncFrequency = 0;
    cfg.importer.continousImportPolling = 0;
    cfg.importer.threads = 3;
    cfg.normalizer.archiveDir = Files.createTempDir();
    cfg.normalizer.scratchDir = Files.createTempDir();
    cfg.db.host = "localhost";
    cfg.db.database = "colplus";
    cfg.db.user = "postgres";
    cfg.db.password = "postgres";
    cfg.es.hosts = "localhost";
    cfg.es.ports = "9200";
    cfg.es.nameUsage.modelClass = "org.col.es.model.EsNameUsage";

    return cfg;
  }

  @Before
  public void init() throws Exception {
    MetricRegistry metrics = new MetricRegistry();

    final AdminServerConfig cfg = provideConfig();
    InitDbCmd.execute(cfg);

    RestClient esClient = new EsClientFactory(cfg.es).createClient();

    hc = new HttpClientBuilder(metrics).using(cfg.client).build("local");
    importManager = new ImportManager(cfg, metrics, hc, PgSetupRule.getSqlSessionFactory(),
        NameIndexFactory.passThru(),esClient);
    importManager.start();
  }

  @After
  public void shutdown() throws Exception {
    importManager.stop();
    hc.close();
    esClient.close();
  }

  /**
   * Try with 3 small parallel datasets
   */
  @Test
  public void debugParalle() throws Exception {
    importManager.submit(1000, true);
    importManager.submit(1006, true);
    importManager.submit(1007, true);

    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }

  @Test
  public void debugImport() throws Exception {
    importManager.submit(11, true);
    Thread.sleep(1000);
    while (importManager.hasRunning()) {
      Thread.sleep(1000);
    }
  }
}

package org.col.importer;

import java.net.URL;
import java.nio.file.Paths;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.DatasetType;
import org.col.api.vocab.Issue;
import org.col.command.initdb.InitDbCmd;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.config.ImporterConfig;
import org.col.config.NormalizerConfig;
import org.col.dao.DatasetImportDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.TestDataRule;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.col.img.ImageService;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbFactory;
import org.col.matching.NameIndexFactory;
import org.junit.*;

import static org.junit.Assert.assertTrue;

/**
 * Checks for unit test ColDP archives under resources/integrity-checks/
 */
public class IntegrityChecksIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  @Before
  public void initCfg() {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    dataset = new Dataset();
    dataset.setType(DatasetType.OTHER);
    dataset.setOrigin(DatasetOrigin.UPLOADED);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    InitDbCmd.setupStandardPartitions(testDataRule.getSqlSession());
    testDataRule.commit();
 }
  
  @After
  public void cleanup() {
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
  
  void normalizeAndImport(String key) {
    URL url = getClass().getResource("/integrity-checks/" + key);
    dataset.setDataFormat(DataFormat.COLDP);
    try {
      // insert trusted dataset
      dataset.setTitle("Integrity check " + key);
      
      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new key, usually 2000!
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      store.put(dataset);
      Normalizer norm = new Normalizer(store, Paths.get(url.toURI()), NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer), ImageService.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), aNormalizer, icfg);
      importer.call();
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo())
        .generateMetrics(dataset.getKey());
  }
  
  
  @Test
  //@Ignore("depends on duplicate detection working")
  public void testA1() throws Exception {
    normalizeAndImport("A1");

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {

      NameMapper nm = session.getMapper(NameMapper.class);
      Name m = nm.get(new DSIDValue<>(dataset.getKey(), "1"));
      VerbatimRecordMapper v = session.getMapper(VerbatimRecordMapper.class);
      VerbatimRecord vr = v.get(DSID.vkey(m));

      // missing species epithet
      assertTrue(vr.hasIssue(Issue.INDETERMINED));

    }
  }
  
}

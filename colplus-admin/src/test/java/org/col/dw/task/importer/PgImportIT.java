package org.col.dw.task.importer;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import jersey.repackaged.com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.dw.api.*;
import org.col.dw.api.vocab.DistributionStatus;
import org.col.dw.api.vocab.Gazetteer;
import org.col.dw.api.vocab.Language;
import org.col.dw.config.ImporterConfig;
import org.col.dw.config.NormalizerConfig;
import org.col.dw.dao.NameDao;
import org.col.dw.task.importer.neo.NormalizerStore;
import org.col.dw.task.importer.dwca.Normalizer;
import org.col.dw.task.importer.neo.NeoDbFactory;
import org.col.dw.dao.TaxonDao;
import org.col.dw.db.mapper.DatasetMapper;
import org.col.dw.db.mapper.InitMybatisRule;
import org.col.dw.db.mapper.PgSetupRule;
import org.junit.*;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class PgImportIT {
	private NormalizerStore store;
	private NormalizerConfig cfg;
	private ImporterConfig icfg = new ImporterConfig();
	private Dataset dataset;

	@ClassRule
	public static PgSetupRule pgSetupRule = new PgSetupRule();

	@Rule
	public InitMybatisRule initMybatisRule = InitMybatisRule.empty();

	@Before
	public void initCfg() throws Exception {
		cfg = new NormalizerConfig();
		cfg.directory = Files.createTempDir();
		dataset = new Dataset();
	}

	@After
	public void cleanup() throws Exception {
		if (store != null) {
			// store is close by Normalizer.run method already
			FileUtils.deleteQuietly(cfg.directory);
		}
	}

	void normalizeAndImport(int dwcaKey) throws Exception {
		URL dwcaUrl = getClass().getResource("/dwca/" + dwcaKey);
		Path dwca = Paths.get(dwcaUrl.toURI());

		// insert dataset
		dataset.setTitle("Test Dataset " + dwcaKey);
		SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
		// this creates a new datasetKey, usually 1!
		session.getMapper(DatasetMapper.class).create(dataset);
		session.commit();
		session.close();

		// normalize
		Normalizer norm = new Normalizer(NeoDbFactory.create(cfg, dataset.getKey()), dwca.toFile());
		norm.run();

		// import into postgres
		store = NeoDbFactory.open(cfg, dataset.getKey());
		PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(),
		    icfg);
		importer.run();
	}

	@Test
	public void testDwca1() throws Exception {
		normalizeAndImport(1);

		// verify results
		try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
			TaxonDao tdao = new TaxonDao(session);
			NameDao ndao = new NameDao(session);

			// check basionym
			Name n1006 = ndao.get(ndao.lookupKey("1006", dataset.getKey()));
			assertEquals("Leontodon taraxacoides", n1006.getScientificName());

			Name bas = ndao.get(n1006.getBasionymKey());
			assertEquals("Leonida taraxacoida", bas.getScientificName());
			assertEquals("1006-s3", bas.getId());

			// check taxon parents
			assertParents(tdao, "1006", "102", "30", "20", "10", "1");

			// TODO: check synonym
		}
	}

	@Test
	public void testSupplementary() throws Exception {
		normalizeAndImport(24);

		// verify results
		try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
			TaxonDao tdao = new TaxonDao(session);

			// check species name
			Taxon tax = tdao.get(tdao.lookupKey("1000",dataset.getKey()));
			assertEquals("Crepis pulchra", tax.getName().getScientificName());

			TaxonInfo info = tdao.getTaxonInfo(tax.getKey());
			// check vernaculars
			Map<Language, String> expV = Maps.newHashMap();
			expV.put(Language.GERMAN, "Schöner Pippau");
			expV.put(Language.ENGLISH, "smallflower hawksbeard");
			assertEquals(expV.size(), info.getVernacularNames().size());
			for (VernacularName vn : info.getVernacularNames()) {
				assertEquals(expV.remove(vn.getLanguage()), vn.getName());
			}
			assertTrue(expV.isEmpty());

			// check distributions
			Set<Distribution> expD = Sets.newHashSet();
			expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
			expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));

			assertEquals(expD.size(), info.getDistributions().size());
			// remove dist keys before we check equality
			info.getDistributions().forEach(d -> d.setKey(null));
			Set<Distribution> imported = Sets.newHashSet(info.getDistributions());

			Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
			for (Distribution d : diff) {
				System.out.println(d);
			}
			assertEquals(expD, imported);
		}
	}

	private Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
		Distribution d = new Distribution();
		d.setArea(area);
		d.setAreaStandard(standard);
		d.setStatus(status);
		return d;
	}

	private void assertParents(TaxonDao tdao, String taxonID, String... parentIds) {
		final LinkedList<String> expected = new LinkedList<String>(Arrays.asList(parentIds));
		Taxon t = tdao.get(tdao.lookupKey(taxonID,dataset.getKey()));
		while (t.getParentKey() != null) {
			Taxon parent = tdao.get(t.getParentKey());
			assertEquals(expected.pop(), parent.getId());
			t = parent;
		}
		assertTrue(expected.isEmpty());
	}

}
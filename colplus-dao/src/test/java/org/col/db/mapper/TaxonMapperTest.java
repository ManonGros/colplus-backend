package org.col.db.mapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.db.MybatisTestUtils;
import org.col.dao.NameDao;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TaxonMapperTest extends DatasetCRUDTest<Taxon, TaxonMapper> {
  
  private static final int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
  private Sector sector;
  
  public TaxonMapperTest() {
    super(TaxonMapper.class);
  }
  
  @Before
  public void init() {
    // create a few draft taxa to attach sectors to
    MybatisTestUtils.populateDraftTree(session());

    sector = SectorMapperTest.create();
    sector.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    sector.getTarget().setId("t4");

    mapper(SectorMapper.class).create(sector);
  }
  
  @Override
  void updateTestObj(Taxon obj) {
  
  }
  
  @Override
  Taxon createTestEntity() {
    Taxon t = TestEntityGenerator.newTaxon();
    // manually set the child count which is populated on read only
    t.setChildCount(0);
    t.setSectorKey(sector.getKey());
    return t;
  }
  
  @Override
  Taxon removeDbCreatedProps(Taxon obj) {
    return TestEntityGenerator.nullifyUserDate(obj);
  }
  
  @Test
  public void count() throws Exception {
    // 2 Taxa pre-inserted through InitMybatisRule.apple()
    mapper().create(TestEntityGenerator.newTaxon("t2"));
    mapper().create(TestEntityGenerator.newTaxon("t3"));
    mapper().create(TestEntityGenerator.newTaxon("t4"));
    generateDatasetImport(DATASET11.getKey());
    
    assertEquals(5, mapper().count(DATASET11.getKey()));
  }
  
  @Test
  public void list() throws Exception {
    List<Taxon> taxa = new ArrayList<>();
    taxa.add(TestEntityGenerator.newTaxon("t1"));
    taxa.add(TestEntityGenerator.newTaxon("t2"));
    taxa.add(TestEntityGenerator.newTaxon("t3"));
    taxa.add(TestEntityGenerator.newTaxon("t4"));
    taxa.add(TestEntityGenerator.newTaxon("t5"));
    taxa.add(TestEntityGenerator.newTaxon("t6"));
    taxa.add(TestEntityGenerator.newTaxon("t7"));
    taxa.add(TestEntityGenerator.newTaxon("t8"));
    taxa.add(TestEntityGenerator.newTaxon("t9"));
    for (Taxon t : taxa) {
      mapper().create(t);
    }
    commit();
    
    // get first page
    Page p = new Page(0, 3);
    
    List<Taxon> res = mapper().list(DATASET11.getKey(), p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertEquals(TestEntityGenerator.TAXON1.getId(), res.get(0).getId());
    assertEquals(TestEntityGenerator.TAXON2.getId(), res.get(1).getId());
    assertEquals(taxa.get(0).getId(), res.get(2).getId());
    
    p.next();
    res = mapper().list(DATASET11.getKey(), p);
    assertEquals(3, res.size());
    assertEquals(taxa.get(1).getId(), res.get(0).getId());
    assertEquals(taxa.get(2).getId(), res.get(1).getId());
    assertEquals(taxa.get(3).getId(), res.get(2).getId());
    
  }
  
  @Test
  public void list3() throws Exception {
    List<Taxon> taxa = new ArrayList<>();
    taxa.add(TestEntityGenerator.newTaxon("t1"));
    taxa.add(TestEntityGenerator.newTaxon("t2"));
    taxa.add(TestEntityGenerator.newTaxon("t3"));
    taxa.add(TestEntityGenerator.newTaxon("t4"));
    taxa.add(TestEntityGenerator.newTaxon("t5"));
    taxa.add(TestEntityGenerator.newTaxon("t6"));
    taxa.add(TestEntityGenerator.newTaxon("t7"));
    taxa.add(TestEntityGenerator.newTaxon("t8"));
    taxa.add(TestEntityGenerator.newTaxon("t9"));
    for (Taxon t : taxa) {
      mapper().create(t);
    }
    commit();
    
    // get first page
    Page p = new Page(0, 1000);
    
    List<Taxon> res = mapper().listRoot(DATASET11.getKey(), p);
    // Only the 2 pre-inserted root taxa.
    assertEquals(2, res.size());
  }
  
  @Test
  public void countChildren() throws Exception {
    Taxon parent = TestEntityGenerator.newTaxon("parent-1");
    mapper().create(parent);
    
    Taxon c1 = TestEntityGenerator.newTaxon("child-1");
    c1.setParentId(parent.getId());
    mapper().create(c1);
    
    Taxon c2 = TestEntityGenerator.newTaxon("child-2");
    c2.setParentId(parent.getId());
    mapper().create(c2);
    
    Taxon c3 = TestEntityGenerator.newTaxon("child-3");
    c3.setParentId(parent.getId());
    mapper().create(c3);
    
    commit();
    
    int res = mapper().countChildren(parent.getDatasetKey(), parent.getId());
    assertEquals(3, res);
  }
  
  @Test
  public void children() throws Exception {
    Taxon parent = TestEntityGenerator.newTaxon("parent-1");
    mapper().create(parent);
    
    NameDao nameDao = new NameDao(initMybatisRule.getSqlSession());
    
    Name n1 = TestEntityGenerator.newName("XXX");
    n1.setScientificName("XXX");
    n1.setRank(Rank.SUBGENUS);
    nameDao.create(n1);
    
    Taxon c1 = TestEntityGenerator.newTaxon("child-1");
    c1.setName(n1);
    c1.setParentId(parent.getId());
    mapper().create(c1);
    
    Name n2 = TestEntityGenerator.newName("YYY");
    n1.setScientificName("YYY");
    n2.setRank(Rank.FAMILY);
    nameDao.create(n2);
    
    Taxon c2 = TestEntityGenerator.newTaxon("child-2");
    c2.setName(n2);
    c2.setParentId(parent.getId());
    mapper().create(c2);
    
    Name n3 = TestEntityGenerator.newName("ZZZ");
    n3.setScientificName("ZZZ");
    n3.setRank(Rank.INFRASPECIFIC_NAME);
    nameDao.create(n3);
    
    Taxon c3 = TestEntityGenerator.newTaxon("child-3");
    c3.setName(n3);
    c3.setParentId(parent.getId());
    mapper().create(c3);
    
    Name n4 = TestEntityGenerator.newName("AAA");
    n4.setScientificName("AAA");
    n4.setRank(Rank.SUBGENUS);
    nameDao.create(n4);
    
    Taxon c4 = TestEntityGenerator.newTaxon("child-4");
    c4.setName(n4);
    c4.setParentId(parent.getId());
    mapper().create(c4);
    
    commit();
    
    List<Taxon> res = mapper().children(parent.getDatasetKey(), parent.getId(), new Page(0, 5));
    
    assertEquals("01", 4, res.size());
    assertEquals(c2.getId(), res.get(0).getId()); // Family YYY
    assertEquals(c4.getId(), res.get(1).getId()); // Subgenus AAA
    assertEquals(c1.getId(), res.get(2).getId()); // Subgenus XXX
    assertEquals(c3.getId(), res.get(3).getId()); // Infraspecific ZZZ
    
  }
  
  private LinkedList<Taxon> createClassification(Taxon root, String... ids) throws Exception {
    LinkedList<Taxon> taxa = Lists.newLinkedList();
    taxa.add(root);
    Taxon p = root;
    for (String id : ids) {
      p = createChild(p, id);
      taxa.add(p);
    }
    return taxa;
  }
  
  private Taxon createChild(Taxon parent, String id) throws Exception {
    Taxon t = TestEntityGenerator.newTaxon(id);
    t.setParentId(parent.getId());
    mapper().create(t);
    return t;
  }
  
  @Test
  public void classification() throws Exception {
    
    Taxon kingdom = TestEntityGenerator.newTaxon("kingdom-01"); // 1
    // Explicitly set to null to override TestEntityGenerator
    kingdom.setParentId(null);
    mapper().create(kingdom);
    
    LinkedList<Taxon> parents =
        createClassification(kingdom, "p1", "c1", "o1", "sf1", "f1", "g1", "sg1", "s1");
    
    commit();
    
    Taxon sp = parents.removeLast();
    List<Taxon> classification = mapper().classification(sp.getDatasetKey(), sp.getId());
    assertEquals(parents.size(), classification.size());
    
    for (Taxon ht : classification) {
      Taxon p = parents.removeLast();
      assertEquals(p.getId(), ht.getId());
    }
  }
  
  @Test
  public void incDatasetSectorCount() throws Exception {
    mapper().incDatasetSectorCount(Datasets.DRAFT_COL, sector.getTarget().getId(), sector.getDatasetKey(), 7);
    TreeNode n = getTreeNode(sector.getTarget().getId());
    // t4 already has count=1 for dataset 11 when draft tree gets populated
    assertEquals(8, (int) n.getDatasetSectors().get(sector.getDatasetKey()));
    // cascades to all parents
    assertEquals(9, (int) getTreeNode("t3").getDatasetSectors().get(sector.getDatasetKey()));
    assertEquals(9, (int) getTreeNode("t2").getDatasetSectors().get(sector.getDatasetKey()));
    assertEquals(9, (int) getTreeNode("t1").getDatasetSectors().get(sector.getDatasetKey()));
  
  
    mapper().incDatasetSectorCount(Datasets.DRAFT_COL, "unreal", sector.getDatasetKey(), 10);
    // cascades to all parents
    assertEquals(9, (int) getTreeNode("t3").getDatasetSectors().get(sector.getDatasetKey()));
    assertEquals(9, (int) getTreeNode("t2").getDatasetSectors().get(sector.getDatasetKey()));
    assertEquals(9, (int) getTreeNode("t1").getDatasetSectors().get(sector.getDatasetKey()));
  
  }
  
  private TreeNode getTreeNode(String id) {
    return session().getMapper(TreeMapper.class).get(Datasets.DRAFT_COL, id);
    
  }
  
}

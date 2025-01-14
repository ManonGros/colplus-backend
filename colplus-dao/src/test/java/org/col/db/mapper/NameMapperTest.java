package org.col.db.mapper;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.vocab.Datasets;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class NameMapperTest extends CRUDPageableTestBase<Name, NameMapper> {
  
  private NameMapper nameMapper;
  
  public NameMapperTest() {
    super(NameMapper.class);
  }
  
  @Before
  public void initMappers() {
    nameMapper = testDataRule.getMapper(NameMapper.class);
  }
  
  static Name create(final String id, final Name basionym) throws Exception {
    Name n = TestEntityGenerator.newName(id);
    n.setHomotypicNameId(basionym.getId());
    n.setAuthorshipNormalized(Lists.newArrayList("linne", "walther"));
    return n;
  }
  
  @Override
  Name createTestEntity(int dkey) {
    return TestEntityGenerator.newName(dkey, "sk1");
  }
  
  @Override
  Name removeDbCreatedProps(Name n) {
    TestEntityGenerator.nullifyDate(n);
    n.setHomotypicNameId(null);
    return n;
  }
  
  @Override
  void updateTestObj(Name n) {
    n.setAuthorship("Berta & Tomate");
  }
  
  @Test
  public void roundtrip2() throws Exception {
    Name n1 = TestEntityGenerator.newName("sk1");
    nameMapper.create(n1);
    assertNotNull(n1.getId());
    commit();
    
    Name n1b = removeDbCreatedProps(nameMapper.get(n1.getKey()));
    assertEquals(removeDbCreatedProps(n1), n1b);
    
    // with explicit homotypic group
    Name n2 = TestEntityGenerator.newName("sk2");
    n2.setHomotypicNameId(n1.getId());
    nameMapper.create(n2);
    
    commit();
    
    // we use a new instance of n1 with just the keys for the equality tests
    // n1 = new Name();
    // n1.setKey(n2.getBasionymKey());
    // n1.setId(n2.getBasionymKey());
    // n2.setBasionymKey(n1);
    
    Name n2b = removeDbCreatedProps(nameMapper.get(n2.getKey()));
    assertEquals(removeDbCreatedProps(n2), n2b);
  }
  
  @Test
  public void count() throws Exception {
    generateDatasetImport(DATASET11.getKey());
    commit();
    assertEquals(5, nameMapper.count(DATASET11.getKey()));
    
    nameMapper.create(TestEntityGenerator.newName());
    nameMapper.create(TestEntityGenerator.newName());
    generateDatasetImport(DATASET11.getKey());
    commit();
    
    assertEquals(7, nameMapper.count(DATASET11.getKey()));
  }
  
  @Test
  public void hasData() throws Exception {
    assertTrue(nameMapper.hasData(DATASET11.getKey()));
    assertFalse(nameMapper.hasData(Datasets.NAME_INDEX));
  }
  
  @Test
  public void basionymGroup() throws Exception {
    Name n2bas = TestEntityGenerator.newName("n2");
    nameMapper.create(n2bas);
    
    Name n1 = create("n1", n2bas);
    nameMapper.create(n1);
    
    Name n3 = create("n3", n2bas);
    nameMapper.create(n3);
    
    Name n4 = create("n4", n2bas);
    nameMapper.create(n4);
    
    commit();
    
    List<Name> s1 = mapper().homotypicGroup(n1.getDatasetKey(), n1.getId());
    assertEquals(4, s1.size());
    
    List<Name> s2 = mapper().homotypicGroup(n2bas.getDatasetKey(), n2bas.getId());
    assertEquals(4, s2.size());
    assertEquals(s1, s2);
    
    List<Name> s3 = mapper().homotypicGroup(n3.getDatasetKey(), n3.getId());
    assertEquals(4, s3.size());
    assertEquals(s1, s3);
    
    List<Name> s4 = mapper().homotypicGroup(n4.getDatasetKey(), n4.getId());
    assertEquals(4, s4.size());
    assertEquals(s1, s4);
  }
  
  /*
   * Checks difference in behaviour between providing non-existing key
   * and providing existing key but without synonyms.
   * yields null (issue #55)
   */
  @Test
  public void basionymGroup2() throws Exception {
    Name n = TestEntityGenerator.newName("nxx");
    mapper().create(n);
    List<Name> s = mapper().homotypicGroup(n.getDatasetKey(), n.getId());
    assertNotNull(s);
    s = mapper().homotypicGroup(11, "-1");
    assertNotNull(s);
    assertEquals(0, s.size());
  }
  
  @Test
  public void listByReference() throws Exception {
    Name acc1 = newAcceptedName("Nom uno");
    nameMapper.create(acc1);
    assertTrue(nameMapper.listByReference(REF1b.getDatasetKey(), REF1b.getId()).isEmpty());
    
    Name acc2 = newAcceptedName("Nom duo");
    acc2.setPublishedInId(REF1b.getId());
    Name acc3 = newAcceptedName("Nom tres");
    acc3.setPublishedInId(REF1b.getId());
    nameMapper.create(acc2);
    nameMapper.create(acc3);
    
    // we have one ref from the apple.sql
    assertEquals(1, nameMapper.listByReference(REF1.getDatasetKey(), REF1.getId()).size());
    assertEquals(2, nameMapper.listByReference(REF1b.getDatasetKey(), REF1b.getId()).size());
  }
  
  private static Name newAcceptedName(String scientificName) {
    return newName(DATASET11.getKey(), scientificName.toLowerCase().replace(' ', '-'), scientificName);
  }
  
}

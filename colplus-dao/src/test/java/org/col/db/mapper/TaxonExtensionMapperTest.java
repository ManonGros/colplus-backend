package org.col.db.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.TestEntityGenerator;
import org.col.api.model.DatasetScopedEntity;
import org.col.api.model.Taxon;
import org.col.api.model.TaxonExtension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

abstract class TaxonExtensionMapperTest<T extends DatasetScopedEntity<Integer>, M extends TaxonExtensionMapper<T>> extends MapperTestBase<M> {
  
  Taxon tax;
  
  public TaxonExtensionMapperTest(Class<M> mapperClazz) {
    super(mapperClazz, TestDataRule.empty());
  }
  
  /**
   * @return at least 3 entities, more allowed !
   */
  abstract List<T> createTestEntities();
  
  @Test
  public void roundtrip() throws Exception {
    // prepare taxon to hook extensions to
    tax = TestEntityGenerator.newTaxon(3);
    insertTaxon(tax);
    
    final List<T> entities = createTestEntities();
    assertTrue("At least 3 test entities are needed", entities.size() > 2);
    List<T> originals = new ArrayList<>();
    for (T obj : entities) {
      obj.setDatasetKey(tax.getDatasetKey());
      // test create
      mapper().create(obj, tax.getId());
      originals.add(TestEntityGenerator.nullifyDate(obj));
    }
    commit();

    // test get
    T obj = TestEntityGenerator.nullifyDate(mapper().get(originals.get(0)));
    assertEquals(obj, originals.get(0));
    
    // test listByTaxon
    List<T> created = TestEntityGenerator.nullifyDate(mapper().listByTaxon(tax));
    assertEquals(originals, created);
  
    // processing
    CountHandler handler = new CountHandler();
    mapper().processDataset(tax.getDatasetKey(), handler);
    assertEquals(1, handler.counter.size());
    assertEquals(originals.size(), (int) handler.counter.get(tax.getId()));
  }
  
  public class CountHandler implements ResultHandler<TaxonExtension<T>> {
    Map<String, Integer> counter = new HashMap<>();
    
    public void handleResult(ResultContext<? extends TaxonExtension<T>> ctx) {
      TaxonExtension<T> te = ctx.getResultObject();
      assertEquals(tax.getId(), te.getTaxonID());
      if (counter.containsKey(te.getTaxonID())) {
        counter.put(te.getTaxonID(), counter.get(te.getTaxonID()) + 1);
      } else {
        counter.put(te.getTaxonID(), 1);
      }
    }
  }
}
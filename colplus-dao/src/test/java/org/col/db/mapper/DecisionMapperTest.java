package org.col.db.mapper;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.TestEntityGenerator;
import org.col.api.model.DataEntity;
import org.col.api.model.DatasetScoped;
import org.col.api.model.EditorialDecision;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.TaxonomicStatus;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;

public class DecisionMapperTest extends CRUDTestBase<Integer, EditorialDecision, DecisionMapper> {
  
  public DecisionMapperTest() {
    super(DecisionMapper.class);
  }
  
  final int catalogeKey = Datasets.DRAFT_COL;
  final int subjectDatasetKey = DATASET11.getKey();
  
  @Test
  public void brokenDecisions() {
    EditorialDecision d1 = createTestEntity(catalogeKey);
    d1.getSubject().setId(TestEntityGenerator.TAXON1.getId());
    mapper().create(d1);

    EditorialDecision d2 = createTestEntity(catalogeKey);
    mapper().create(d2);
    commit();
    
    assertEquals(2, mapper().listBySubjectDataset(catalogeKey,null, null).size());
    assertEquals(2, mapper().listBySubjectDataset(catalogeKey, subjectDatasetKey, null).size());
    assertEquals(1, mapper().listBySubjectDataset(catalogeKey, subjectDatasetKey, TestEntityGenerator.TAXON1.getId()).size());
    assertEquals(1, mapper().subjectBroken(catalogeKey, subjectDatasetKey).size());
  }
  
  @Override
  void updateTestObj(EditorialDecision ed) {
    ed.setNote("My next note");
    ed.setName(TestEntityGenerator.newName("updatedID"));
  }
  
  @Override
  EditorialDecision createTestEntity(int dkey) {
    return create(subjectDatasetKey);
  }

  public static EditorialDecision create(int subjectDatasetKey) {
    EditorialDecision d = new EditorialDecision();
    d.setDatasetKey(Datasets.DRAFT_COL);
    d.setSubjectDatasetKey(subjectDatasetKey);
    d.setSubject(TestEntityGenerator.newSimpleName());
    d.setMode(EditorialDecision.Mode.UPDATE);
    d.setName(TestEntityGenerator.newName());
    d.setStatus(TaxonomicStatus.AMBIGUOUS_SYNONYM);
    d.setExtinct(true);
    d.getLifezones().add(Lifezone.MARINE);
    d.getLifezones().add(Lifezone.BRACKISH);
    d.setNote("I cannot remember why I did this.");
    d.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    d.setModifiedBy(d.getCreatedBy());
    return d;
  }
  
  @Override
  EditorialDecision removeDbCreatedProps(EditorialDecision obj) {
    obj.setCreated(null);
    obj.setModified(null);
    return obj;
  }
  
  @Test
  public void process(){
    // processing
    CountHandler handler = new CountHandler();
    mapper().processDataset(catalogeKey, handler);
    assertEquals(0, handler.counter.size());
  }
  
  public static class CountHandler<T extends DataEntity<Integer> & DatasetScoped> implements ResultHandler<T> {
    Map<Integer, Integer> counter = new HashMap<>();
  
    @Override
    public void handleResult(ResultContext<? extends T> ctx) {
      T d = ctx.getResultObject();
      if (counter.containsKey(d.getDatasetKey())) {
        counter.put(d.getDatasetKey(), counter.get(d.getDatasetKey()) + 1);
      } else {
        counter.put(d.getDatasetKey(), 1);
      }
    }
  }
  
}
package org.col.dao;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.mapper.DecisionMapper;
import org.col.es.name.index.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDao extends EntityDao<Integer, EditorialDecision, DecisionMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionDao.class);
  
  private final NameUsageIndexService indexService;

  public DecisionDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(true, factory, DecisionMapper.class);
    this.indexService = indexService;
  }
  
  public ResultPage<EditorialDecision> list(int datasetKey, Page page) {
    return super.list(mapperClass, datasetKey, page);
  }
  
  @Override
  protected void createAfter(EditorialDecision obj, int user, DecisionMapper mapper, SqlSession session) {
    if (obj.getSubject().getId() != null) {
      indexService.sync(obj.getSubjectDatasetKey(), Lists.newArrayList(obj.getSubject().getId()));
    }
  }
  
  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id.
   * If the previous version referred to a different subject id also update that taxon.
   */
  @Override
  protected void updateAfter(EditorialDecision obj, EditorialDecision old, int user, DecisionMapper mapper, SqlSession session) {
    final List<String> ids = new ArrayList<>();
    if (old != null && old.getSubject().getId() != null && !old.getSubject().getId().equals(obj.getSubject().getId())) {
      ids.add(old.getSubject().getId());
    }
    if (obj.getSubject().getId() != null) {
      ids.add(obj.getSubject().getId());
    }
    indexService.sync(obj.getSubjectDatasetKey(), ids);
  }
  
  @Override
  protected void deleteAfter(Integer key, EditorialDecision old, int user, DecisionMapper mapper, SqlSession session) {
    if (old != null && old.getSubject().getId() != null) {
      indexService.sync(old.getSubjectDatasetKey(), Lists.newArrayList(old.getSubject().getId()));
    }
  }
  
}

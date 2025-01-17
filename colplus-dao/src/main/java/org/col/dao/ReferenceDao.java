package org.col.dao;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.search.ReferenceSearchRequest;
import org.col.common.csl.CslUtil;
import org.col.db.mapper.ReferenceMapper;

public class ReferenceDao extends DatasetEntityDao<String, Reference, ReferenceMapper> {
  
  
  public ReferenceDao(SqlSessionFactory factory) {
    super(false, factory, ReferenceMapper.class);
  }
  
  public Reference get(DSID<String> did, @Nullable String page) {
    Reference ref = super.get(did);
    if (ref == null) {
      return null;
    }
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }
  
  @Override
  public DSID<String> create(Reference r, int user) {
    // build default citation from csl
    if (r.getCitation() == null && r.getCsl() != null) {
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    }
    return super.create(r, user);
  }
  
  @Override
  protected void updateBefore(Reference r, Reference old, int user, ReferenceMapper mapper, SqlSession session) {
    if (r.getCitation() == null && r.getCsl() != null) {
      // build citation from csl
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    } else if (Objects.equals(r.getCitation(), old.getCitation()) && !Objects.equals(r.getCsl(), old.getCsl())) {
      // csl changed, but citation is still the same
      r.setCitation(CslUtil.buildCitation(r.getCsl()));
    }
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  public static DSIDValue<String> copyReference(final SqlSession session, final Reference r, final int targetDatasetKey, int user) {
    final DSIDValue<String> orig = new DSIDValue<>(r);
    newKey(r);
    r.applyUser(user, true);
    r.setDatasetKey(targetDatasetKey);
    session.getMapper(ReferenceMapper.class).create(r);
    return orig;
  }
  
  public ResultPage<Reference> search(int datasetKey, ReferenceSearchRequest nullableReq, Page page) {
    page = page == null ? new Page() : page;
    final ReferenceSearchRequest req = nullableReq == null || nullableReq.isEmpty() ? new ReferenceSearchRequest() : nullableReq;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(ReferenceSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(ReferenceSearchRequest.SortBy.NATIVE);
      }
    } else if (req.getSortBy() == ReferenceSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(ReferenceSearchRequest.SortBy.NATIVE);
    }
    
    try (SqlSession session = factory.openSession()) {
      ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
      List<Reference> result = mapper.search(datasetKey, req, page);
      return new ResultPage<>(page, result, () -> mapper.searchCount(datasetKey, req));
    }
  }
}

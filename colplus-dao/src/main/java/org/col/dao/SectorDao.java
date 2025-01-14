package org.col.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorDao extends EntityDao<Integer, Sector, SectorMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorDao.class);
  
  public SectorDao(SqlSessionFactory factory) {
    super(true, factory, SectorMapper.class);
  }
  
  public ResultPage<Sector> list(int datasetKey, Page page) {
    return super.list(mapperClass, datasetKey, page);
  }
  
  @Override
  public Integer create(Sector obj, int user) {
    obj.applyUser(user);
    try (SqlSession session = factory.openSession(ExecutorType.SIMPLE, false)) {
      SectorMapper mapper = session.getMapper(SectorMapper.class);
  
      final DSID<String> did = obj.getTargetAsDSID();
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
  
      // reload full source and target
      Taxon subject = tm.get(obj.getSubjectAsDSID());
      if (subject == null) {
        throw new IllegalArgumentException("subject ID " + obj.getSubject().getId() + " not existing in dataset " + obj.getSubjectDatasetKey());
      }
      obj.setSubject(subject.toSimpleName());
  
      Taxon target  = tm.get(obj.getTargetAsDSID());
      if (target == null) {
        throw new IllegalArgumentException("target ID " + obj.getTarget().getId() + " not existing in catalogue " + obj.getDatasetKey());
      }
      obj.setTarget(target.toSimpleName());
      
      
      // create sector key
      mapper.create(obj);
      final Integer secKey = obj.getKey();
  
      List<Taxon> toCopy = new ArrayList<>();
      // create direct children in catalogue
      if (Sector.Mode.ATTACH == obj.getMode()) {
        // one taxon in ATTACH mode
        toCopy.add(subject);
      } else {
        // several taxa in MERGE mode
        toCopy = tm.children(obj.getSubjectAsDSID(), new Page());
      }
  
      for (Taxon t : toCopy) {
        t.setSectorKey(secKey);
        TaxonDao.copyTaxon(session, t, did, user, Collections.emptySet());
      }
  
      incSectorCounts(session, obj, 1);
  
      session.commit();
      return secKey;
    }
    
  }
  
  @Override
  protected void updateAfter(Sector obj, Sector old, int user, SectorMapper mapper, SqlSession session) {
    if (old.getTarget() == null || obj.getTarget() == null || !Objects.equals(old.getTarget().getId(), obj.getTarget().getId())) {
      incSectorCounts(session, obj, 1);
      incSectorCounts(session, old, -1);
    }
  }
  
  @Override
  protected void deleteAfter(Integer key, Sector old, int user, SectorMapper mapper, SqlSession session) {
    incSectorCounts(session, old, -1);
  }
  
  public static void incSectorCounts(SqlSession session, Sector s, int delta) {
    if (s != null && s.getTarget() != null) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      tm.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), delta);
    }
  }
}

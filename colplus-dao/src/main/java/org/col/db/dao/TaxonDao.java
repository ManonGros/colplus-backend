package org.col.db.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaxonDao {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonDao.class);
  
  private final SqlSession session;
  private final DescriptionMapper deMapper;
  private final DistributionMapper diMapper;
  private final MediaMapper mMapper;
  private final NameMapper nMapper;
  private final ReferenceMapper rMapper;
  private final SynonymMapper sMapper;
  private final TaxonMapper tMapper;
  private final VernacularNameMapper vMapper;

  public TaxonDao(SqlSession sqlSession) {
    this.session = sqlSession;
    deMapper = session.getMapper(DescriptionMapper.class);
    diMapper = session.getMapper(DistributionMapper.class);
    mMapper = session.getMapper(MediaMapper.class);
    nMapper = session.getMapper(NameMapper.class);
    rMapper = session.getMapper(ReferenceMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
    tMapper = session.getMapper(TaxonMapper.class);
    vMapper = session.getMapper(VernacularNameMapper.class);
  }
  
  public ResultPage<Taxon> list(Integer datasetKey, Boolean root, Page page) {
    Page p = page == null ? new Page() : page;
    Boolean r = root == null ? Boolean.FALSE : root;
    int total = tMapper.count(datasetKey, r);
    List<Taxon> result = tMapper.list(datasetKey, r, p);
    return new ResultPage<>(p, total, result);
  }
  
  public Taxon get(int datasetKey, String id) {
    if (id == null) return null;
    return tMapper.get(datasetKey, id);
  }
  
  public List<Synonym> getSynonyms(int datasetKey, String nameId) {
    return sMapper.listByName(datasetKey, nameId);
  }

  public Synonym getSynonym(int datasetKey, String nameId) {
    if (nameId != null) {
      List<Synonym> syns = getSynonyms(datasetKey, nameId);
      if (!syns.isEmpty()) {
        if (syns.size() > 1) {
          LOG.debug("Multiple synonyms found for nameID {}", nameId);
        }
        return syns.get(0);
      }
    }
    return null;
  }
  
  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(Taxon taxon) {
    return getSynonymy(taxon.getDatasetKey(), taxon.getId());
  }
  
  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int datasetKey, String taxonId) {
    Name accName = nMapper.getByTaxon(datasetKey, taxonId);
    Synonymy syn = new Synonymy();
    // get all synonyms and misapplied name
    // they come ordered by status, then homotypic group so its easy to arrange them
    List<Name> group = Lists.newArrayList();
    for (Synonym s : sMapper.listByTaxon(datasetKey, taxonId)) {
      if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
        syn.addMisapplied(new NameAccordingTo(s.getName(), s.getAccordingTo()));
      } else {
        if (accName.getHomotypicNameId().equals(s.getName().getHomotypicNameId())) {
          syn.getHomotypic().add(s.getName());
        } else {
          if (!group.isEmpty()
              && !group.get(0).getHomotypicNameId().equals(s.getName().getHomotypicNameId())) {
            // new heterotypic group
            syn.addHeterotypicGroup(group);
            group = Lists.newArrayList();
          }
          // add to group
          group.add(s.getName());
        }
      }
    }
    if (!group.isEmpty()) {
      syn.addHeterotypicGroup(group);
    }
    
    return syn;
  }
  
  public List<Taxon> getClassification(Taxon taxon) {
    return getClassification(taxon.getDatasetKey(), taxon.getId());
  }
  
  public List<Taxon> getClassification(int datasetKey, String key) {
    return tMapper.classification(datasetKey, key);
  }
  
  public ResultPage<Taxon> getChildren(int datasetKey, String key, Page page) {
    Page p = page == null ? new Page() : page;
    int total = tMapper.countChildren(datasetKey, key);
    List<Taxon> result = tMapper.children(datasetKey, key, p);
    return new ResultPage<>(p, total, result);
  }
  
  public void create(Taxon taxon) {
    tMapper.create(taxon);
  }
  
  public TaxonInfo getTaxonInfo(int datasetKey, String key) {
    return getTaxonInfo(tMapper.get(datasetKey, key));
  }
  
  public TaxonInfo getTaxonInfo(final Taxon taxon) {
    // main taxon object
    if (taxon == null) {
      return null;
    }
    
    TaxonInfo info = new TaxonInfo();
    info.setTaxon(taxon);
    info.setTaxonReferences(rMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));

    // add all supplementary taxon infos
    info.setDescriptions(deMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setDistributions(diMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setMedia(mMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));
    info.setVernacularNames(vMapper.listByTaxon(taxon.getDatasetKey(), taxon.getId()));

    // all reference keys so we can select their details at the end
    Set<String> refIds = new HashSet<>();
    refIds.add(taxon.getName().getPublishedInId());
    refIds.addAll(info.getTaxonReferences());
    info.getDescriptions().forEach(d -> refIds.add(d.getReferenceId()));
    info.getDistributions().forEach(d -> refIds.add(d.getReferenceId()));
    info.getMedia().forEach(m -> refIds.add(m.getReferenceId()));
    info.getVernacularNames().forEach(d -> refIds.add(d.getReferenceId()));
    // make sure we did not add null by accident
    refIds.remove(null);
    
    if (!refIds.isEmpty()) {
      List<Reference> refs = rMapper.listByIds(taxon.getDatasetKey(), refIds);
      info.addReferences(refs);
    }
    
    return info;
  }
  
}

package org.col.api.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public abstract class NameUsageBase extends DatasetScopedEntity<String> implements NameUsage {
  
  private Integer sectorKey;
  private Integer verbatimKey;
  @Nonnull
  private Name name;
  @Nonnull
  private TaxonomicStatus status;
  @Nonnull
  private Origin origin;
  private String parentId;
  private String accordingTo;
  private URI webpage;
  private String remarks;
  /**
   * All bibliographic reference ids for the given name usage
   */
  private List<String> referenceIds = new ArrayList<>();
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  @Override
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }
  
  @JsonIgnore
  public void setStatusIfNull(TaxonomicStatus status) {
    if (this.status == null) {
      this.status = Preconditions.checkNotNull(status);
    }
  }
  
  @JsonIgnore
  public boolean isProvisional() {
    return status == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
  }
  
  @Override
  public Origin getOrigin() {
    return origin;
  }
  
  @Override
  public void setOrigin(Origin origin) {
    this.origin = origin;
  }
  
  public String getParentId() {
    return parentId;
  }
  
  public void setParentId(String key) {
    this.parentId = key;
  }
  
  @Override
  public String getAccordingTo() {
    return accordingTo;
  }
  
  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }
  
  public void addAccordingTo(String accordingTo) {
    if (!StringUtils.isBlank(accordingTo)) {
      this.accordingTo = this.accordingTo == null ? accordingTo.trim() : this.accordingTo + " " + accordingTo.trim();
    }
  }
  
  @Override
  public String getRemarks() {
    return remarks;
  }
  
  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public List<String> getReferenceIds() {
    return referenceIds;
  }
  
  public void setReferenceIds(List<String> referenceIds) {
    this.referenceIds = referenceIds;
  }
  
  public SimpleName toSimpleName() {
    return new SimpleName(getId(), name.getScientificName(), name.getAuthorship(), name.getRank());
  }
  
  public URI getWebpage() {
    return webpage;
  }
  
  public void setWebpage(URI webpage) {
    this.webpage = webpage;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageBase that = (NameUsageBase) o;
    return Objects.equals(sectorKey, that.sectorKey) &&
        Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(name, that.name) &&
        status == that.status &&
        origin == that.origin &&
        Objects.equals(parentId, that.parentId) &&
        Objects.equals(accordingTo, that.accordingTo) &&
        Objects.equals(webpage, that.webpage) &&
        Objects.equals(remarks, that.remarks) &&
        Objects.equals(referenceIds, that.referenceIds);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, verbatimKey, name, status, origin, parentId, accordingTo, webpage, remarks, referenceIds);
  }
}

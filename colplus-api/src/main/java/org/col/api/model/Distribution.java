package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

/**
 *
 */
public class Distribution extends DatasetScopedEntity<Integer> implements Referenced, VerbatimEntity {
  
  private Integer verbatimKey;
  private String area;
  private Gazetteer gazetteer;
  private DistributionStatus status;
  private String referenceId;
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public String getArea() {
    return area;
  }
  
  public void setArea(String area) {
    this.area = area;
  }
  
  public Gazetteer getGazetteer() {
    return gazetteer;
  }
  
  public void setGazetteer(Gazetteer gazetteer) {
    this.gazetteer = gazetteer;
  }
  
  public DistributionStatus getStatus() {
    return status;
  }
  
  public void setStatus(DistributionStatus status) {
    this.status = status;
  }
  
  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }
  
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Distribution that = (Distribution) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(area, that.area) &&
        gazetteer == that.gazetteer &&
        status == that.status &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, area, gazetteer, status, referenceId);
  }
  
  @Override
  public String toString() {
    return status == null ? "Unknown" : status + " in " + gazetteer + ":" + area;
  }
}

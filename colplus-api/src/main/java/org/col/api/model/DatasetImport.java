package org.col.api.model;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import org.col.api.vocab.ImportState;
import org.gbif.dwc.terms.Term;

/**
 * Metrics and import details about a single dataset import event.
 */
public class DatasetImport extends ImportMetrics<ImportState> {
  
  
  private URI downloadUri;
  
  /**
   * Last modification date of the downloaded file
   */
  private LocalDateTime download;
  
  /**
   * MD5 Hash of raw archive file.
   * Present only if downloaded.
   */
  private String md5;
  
  private Integer verbatimCount;
  
  private Map<Term, Integer> verbatimByTypeCount = Maps.newHashMap();
  
  /**
   * Map of row types that return a map of terms and their counts of verbatim records with that type
   */
  private Map<Term, Map<Term, Integer>> verbatimByTermCount = Maps.newHashMap();

  public URI getDownloadUri() {
    return downloadUri;
  }
  
  public void setDownloadUri(URI downloadUri) {
    this.downloadUri = downloadUri;
  }
  
  public LocalDateTime getDownload() {
    return download;
  }
  
  public void setDownload(LocalDateTime download) {
    this.download = download;
  }
  
  public String getMd5() {
    return md5;
  }
  
  public void setMd5(String md5) {
    this.md5 = md5;
  }
  
  public Integer getVerbatimCount() {
    return verbatimCount;
  }
  
  public void setVerbatimCount(Integer verbatimCount) {
    this.verbatimCount = verbatimCount;
  }
  
  public Map<Term, Integer> getVerbatimByTypeCount() {
    return verbatimByTypeCount;
  }
  
  public void setVerbatimByTypeCount(Map<Term, Integer> verbatimByTypeCount) {
    this.verbatimByTypeCount = verbatimByTypeCount;
  }
  
  public Map<Term, Map<Term, Integer>> getVerbatimByTermCount() {
    return verbatimByTermCount;
  }
  
  public void setVerbatimByTermCount(Map<Term, Map<Term, Integer>> verbatimByTermCount) {
    this.verbatimByTermCount = verbatimByTermCount;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DatasetImport that = (DatasetImport) o;
    return Objects.equals(downloadUri, that.downloadUri) &&
        Objects.equals(download, that.download) &&
        Objects.equals(md5, that.md5) &&
        Objects.equals(verbatimCount, that.verbatimCount) &&
        Objects.equals(verbatimByTypeCount, that.verbatimByTypeCount) &&
        Objects.equals(verbatimByTermCount, that.verbatimByTermCount);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), downloadUri, download, md5, verbatimCount, verbatimByTypeCount, verbatimByTermCount);
  }
  
  @Override
  public String attempt() {
    return getDatasetKey() + " - " + getAttempt();
  }
  
}

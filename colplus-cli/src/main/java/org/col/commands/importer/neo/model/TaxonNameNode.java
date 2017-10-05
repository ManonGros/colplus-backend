package org.col.commands.importer.neo.model;

import com.google.common.base.Joiner;
import org.col.api.Name;
import org.col.api.Taxon;
import org.col.api.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.neo4j.graphdb.Node;

import java.util.Objects;

/**
 * Simple wrapper to hold a neo node together with all data for a record
 * inlcuding a name and a taxon instance.
 * <p>
 * The modified flag can be used to (manually) track if an instance has changed and needs to be persisted.
 */
public class TaxonNameNode implements NeoTaxon {
  private final static Joiner remarkJoiner = Joiner.on("\n").skipNulls();

  public Node node;
  public Taxon taxon;
  public Name name;
  public VerbatimRecord verbatim;

  public TaxonNameNode() {
  }

  public TaxonNameNode(Name name, Taxon taxon, VerbatimRecord verbatim) {
    this(null, name, taxon, verbatim);
  }

  public TaxonNameNode(Node node, Name name, Taxon taxon, VerbatimRecord verbatim) {
    this.node = node;
    this.name = name;
    this.taxon = taxon;
    this.verbatim = verbatim;
  }

  @Override
  public Node getNode() {
    return node;
  }

  @Override
  public void setNode(Node node) {
    this.node = node;
  }

  @Override
  public String getTaxonID() {
    return taxon.getId();
  }

  @Override
  public String getScientificName() {
    return name.getScientificName();
  }

  @Override
  public String getAuthorship() {
    return name.getAuthorship();
  }

  @Override
  public Rank getRank() {
    return name.getRank();
  }

  @Override
  public TaxonomicStatus getStatus() {
    return taxon.getStatus();
  }

  @Override
  public void setStatus(TaxonomicStatus status) {
    taxon.setStatus(status);
  }

  @Override
  public void setAcceptedKey(int key) {
  }

  @Override
  public void setAccepted(String scientificName) {

  }

  @Override
  public void setBasionymKey(int key) {

  }

  @Override
  public void setBasionym(String scientificName) {

  }

  @Override
  public void setParentKey(int key) {

  }

  @Override
  public void addIssue(Issue issues) {
    verbatim.addIssue(issues);
  }

  /**
   * Adds a string remark to the taxonRemarks property of a usage but does not flush the change into the storage.
   * You need to make sure the usage is stored afterwards to not lose it.
   * Existing remarks are left untouched and the new string is appended.
   */
  @Override
  public void addRemark(String remark) {
    name.setRemarks(remarkJoiner.join(name.getRemarks(), remark));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaxonNameNode that = (TaxonNameNode) o;
    return Objects.equals(node, that.node) &&
        Objects.equals(taxon, that.taxon) &&
        Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, taxon, name);
  }
}
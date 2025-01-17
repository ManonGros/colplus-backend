package org.col.importer.neo.model;

import org.neo4j.graphdb.Label;

/**
 *
 */
public enum Labels implements Label {
  /**
   * Applied to nodes that contain a name
   */
  NAME,
  
  /**
   * Additional super label for Taxon or Synonym
   */
  USAGE,

  /**
   * Accepted taxa only
   */
  TAXON,
  
  /**
   * Synonyms only
   */
  SYNONYM,
  
  /**
   * Basionym with at least one HAS_BASIONYM relation
   */
  BASIONYM,
  
  ROOT
}

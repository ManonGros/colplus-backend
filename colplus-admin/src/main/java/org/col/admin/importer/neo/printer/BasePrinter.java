package org.col.admin.importer.neo.printer;

import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.RankedUsage;
import org.col.admin.importer.neo.traverse.StartEndHandler;
import org.neo4j.graphdb.Node;

abstract class BasePrinter implements StartEndHandler, TreePrinter {
  
  private final boolean startOnly;
  
  protected BasePrinter(boolean startOnly) {
    this.startOnly = startOnly;
  }
  
  @Override
  public void start(Node n) {
    start(NeoProperties.getRankedUsage(n));
  }
  
  @Override
  public void end(Node n) {
    if (!startOnly) {
      end(NeoProperties.getRankedUsage(n));
    }
  }
  
  @Override
  public void end(RankedUsage u) {
    // by default implement nothing to simplify printers with just a start call
  }
  
  
  @Override
  public void close() {
  
  }
}

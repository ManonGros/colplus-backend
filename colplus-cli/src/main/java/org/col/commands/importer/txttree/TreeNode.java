package org.col.commands.importer.txttree;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.col.api.vocab.Rank;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.col.commands.importer.neo.printer.TxtPrinter.BASIONYM_SYMBOL;
import static org.col.commands.importer.neo.printer.TxtPrinter.SYNONYM_SYMBOL;

/**
 * Simple bean for representing a node in a taxonomy trees.
 */
public class TreeNode {
  public final String name;
  public final Rank rank;
  public final boolean basionym;
  public final List<TreeNode> synonyms = Lists.newArrayList();
  public final LinkedList<TreeNode> children = Lists.newLinkedList();

  public TreeNode(String name, Rank rank, boolean isBasionym) {
    this.name = name;
    this.rank = rank;
    this.basionym = isBasionym;
  }

  @Override
  public String toString() {
    return name;
  }

  public void print(Appendable out, int level, boolean synonym) throws IOException {
    out.append(StringUtils.repeat(" ", level * 2));
    if (synonym) {
      out.append(SYNONYM_SYMBOL);
    }
    if (basionym) {
      out.append(BASIONYM_SYMBOL);
    }
    out.append(name);
    if (rank != Rank.UNRANKED) {
      out.append(" [");
      out.append(rank.name().toLowerCase());
      out.append("]");
    }
    out.append("\n");
    // recursive
    for (TreeNode n : synonyms) {
      n.print(out, level + 1, true);
    }
    for (TreeNode n : children) {
      n.print(out, level + 1, false);
    }
  }
}

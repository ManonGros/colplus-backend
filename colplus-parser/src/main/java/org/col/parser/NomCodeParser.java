package org.col.parser;

import org.gbif.nameparser.api.NomCode;

/**
 *
 */
public class NomCodeParser extends EnumParser<NomCode> {
  public static final NomCodeParser PARSER = new NomCodeParser();

  public NomCodeParser() {
    super("nomcode.csv", NomCode.class);
    for (NomCode nc : NomCode.values()) {
      add(nc.getAcronym(), nc);
      add(nc.getTitle(), nc);
    }
  }

}

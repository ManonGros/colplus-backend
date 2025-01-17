
package org.col.matching;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Name;
import org.col.api.model.NameMatch;
import org.col.api.vocab.Datasets;
import org.col.common.tax.AuthorshipNormalizer;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameIndexFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NameIndexFactory.class);
  
  /**
   * @return NameIndex that returns no match for any query
   */
  public static NameIndex passThru() {
    return new NameIndex() {
      @Override
      public NameMatch match(Name name, boolean allowInserts, boolean verbose) {
        return NameMatch.noMatch();
      }
      
      @Override
      public long size() {
        return 0;
      }
      
      @Override
      public void add(Name name) {
      }
      
    };
  }
  
  /**
   * Returns a persistent index if location is given, otherwise an in memory one
   */
  public static NameIndex persistentOrMemory(@Nullable File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer aNormalizer) throws IOException {
    NameIndex ni;
    if (location == null) {
      ni = memory(sqlFactory, aNormalizer);
    } else {
      ni = persistent(location, sqlFactory, aNormalizer);
    }
    return ni;
  }
  
  public static NameIndex memory(SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) {
    LOG.info("Use volatile in memory names index");
    return new NameIndexMapDB(DBMaker.memoryDB(), authorshipNormalizer, Datasets.NAME_INDEX, sqlFactory);
  }

  /**
   * Creates or opens a persistent mapdb names index.
   */
  public static NameIndex persistent(File location, SqlSessionFactory sqlFactory, AuthorshipNormalizer authorshipNormalizer) throws IOException {
    if (!location.exists()) {
      FileUtils.forceMkdirParent(location);
      LOG.info("Create persistent names index at {}", location.getAbsolutePath());
    } else {
      LOG.info("Open persistent names index at {}", location.getAbsolutePath());
    }
    DBMaker.Maker maker = DBMaker
        .fileDB(location)
        .fileMmapEnableIfSupported();
    return new NameIndexMapDB(maker, authorshipNormalizer, Datasets.NAME_INDEX, sqlFactory);
  }
  
}

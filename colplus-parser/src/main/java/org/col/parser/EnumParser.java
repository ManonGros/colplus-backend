package org.col.parser;

import java.io.IOException;
import java.util.Map;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.gbif.utils.file.csv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class EnumParser<T extends Enum> extends ParserBase<T> {
  private static final Logger LOG = LoggerFactory.getLogger(EnumParser.class);
  private final Map<String, T> mapping = Maps.newHashMap();
  private final Class<T> enumClass;
  
  public EnumParser(Class<T> enumClass) {
    super(enumClass);
    this.enumClass = enumClass;
    addNativeEnumMappings();
  }
  
  public EnumParser(String mappingResourceFile, Class<T> enumClass) {
    super(enumClass);
    this.enumClass = enumClass;
    // read mappings from resource file
    try {
      LOG.info("Reading mappings from {}", mappingResourceFile);
      CSVReader reader = dictReader(mappingResourceFile);
      while (reader.hasNext()) {
        String[] row = reader.next();
        if (row.length == 0) continue;
        if (row.length == 1) {
          LOG.debug("Ignore unmapped value {} on line {}", row[0], reader.currLineNumber());
          continue;
        }
        if (row.length == 2 && Strings.isNullOrEmpty(row[1])) {
          continue;
        }
        if (row.length > 2) {
          LOG.info("Ignore invalid mapping in {}, line {} with {} columns", mappingResourceFile, reader.currLineNumber(), row.length);
          continue;
        }
        Optional<T> val = Enums.getIfPresent(enumClass, row[1]);
        if (val.isPresent()) {
          add(row[0], val.get());
        } else {
          LOG.warn("Value {} not present in {} enumeration. Ignore mapping to {}", row[1], enumClass.getSimpleName(), row[0]);
        }
      }
      reader.close();
    } catch (IOException e) {
      LOG.error("Failed to load {} parser mappings from {}", enumClass.getSimpleName(), mappingResourceFile, e);
    }
    // finally add native mappings, overriding anything found in files
    addNativeEnumMappings();
  }
  
  private void addNativeEnumMappings() {
    for (T e : enumClass.getEnumConstants()) {
      add(e.name(), e);
    }
  }
  
  /**
   * Adds more mappings to the main mapping dictionary, overwriting any potentially existing values.
   * Keys will be normalized with the same method used for parsing before inserting them to the mapping.
   * Blank strings and null values will be ignored!
   */
  public void add(String key, T value) {
    key = normalize(key);
    if (key != null) {
      this.mapping.put(key, value);
    }
  }
  
  @Override
  String normalize(String x) {
    x = super.normalize(x);
    if (x != null) {
      return x.replaceAll(" +", "");
    }
    return null;
  }
  
  public Class<T> getEnumClass() {
    return enumClass;
  }
  
  @Override
  T parseKnownValues(String upperCaseValue) {
    return mapping.get(upperCaseValue);
  }
}

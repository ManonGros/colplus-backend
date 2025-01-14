package org.col.importer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.col.common.id.IdConverter;
import org.col.common.text.StringUtils;

public class IdGenerator {
  private static final String availChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final char PREFERRED_PREFIX = 'x';
  
  private final Supplier<Integer> counter;
  private final IdConverter idConverter = IdConverter.LATIN32;
  private String prefix;
  
  public IdGenerator() {
    this("", 0);
  }
  
  public IdGenerator(String prefix) {
    this(prefix, 0);
  }
  
  public IdGenerator(String prefix, int start) {
    this.prefix = prefix;
    counter = new AtomicInteger(start)::incrementAndGet;
  }
  
  /**
   * Uses a shared counter
   */
  public IdGenerator(Supplier<Integer> start) {
    this("", start);
  }
  
  /**
   * Uses a shared counter
   */
  public IdGenerator(String prefix, Supplier<Integer> start) {
    this.prefix = prefix;
    counter = start;
  }
  
  private static String smallestNonExistingPrefix(Stream<String> existingIds) {
    final char preferredPrefixChar = PREFERRED_PREFIX;
    final StringBuilder prefix = new StringBuilder(String.valueOf(preferredPrefixChar));
    Set<String> ids = existingIds.filter(s -> s.startsWith(prefix.toString())).collect(Collectors.toSet());
    while (!ids.isEmpty()) {
      Set<Character> idchars = StringUtils.charSet(availChars);
      for (String id : ids) {
        if (id == null || id.length() < 1) continue;
        idchars.remove(id.charAt(0));
      }
      if (idchars.isEmpty()) {
        prefix.append(preferredPrefixChar);
      } else {
        if (idchars.contains(preferredPrefixChar)) {
          prefix.append(preferredPrefixChar);
        } else {
          prefix.append(idchars.iterator().next());
        }
      }
      ids.removeIf(s -> !s.startsWith(prefix.toString()));
    }
    return prefix.toString();
  }
  
  public String getPrefix() {
    return prefix;
  }
  
  public IdGenerator setPrefix(Stream<String> existingIds) {
    this.prefix = smallestNonExistingPrefix(existingIds);
    return this;
  }
  
  public String id(int key) {
    return prefix + idConverter.encode(key);
  }
  
  public String next() {
    return id(counter.get());
  }
  
}

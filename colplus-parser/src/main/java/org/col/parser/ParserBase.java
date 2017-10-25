package org.col.parser;

import com.google.common.base.CharMatcher;
import org.col.util.text.StringUtils;

import java.util.Optional;

/**
 * A base parser implementation dealing with empty, invisible and punctuation values as empty results.
 */
abstract class ParserBase<T> implements Parser<T> {
  private final static CharMatcher VISIBLE = CharMatcher.invisible().negate();
  final Class<T> valueClass;

  ParserBase(Class<T> valueClass) {
    this.valueClass = valueClass;
  }

  @Override
  public Optional<T> parse(String value) throws UnparsableException {
    String x = StringUtils.digitOrAsciiLetters(value);
    if (x == null) {
      // check if we had any not invisible characters - throw Unparsable in such cases
      if (value != null && VISIBLE.matchesAnyOf(value)) {
        throw new UnparsableException(valueClass, value);
      }
      return Optional.empty();
    }

    T val = parseKnownValues(x);
    if (val != null) {
      return Optional.of(val);
    }

    throw new UnparsableException(valueClass, value);
  }

  abstract T parseKnownValues(String upperCaseValue) throws UnparsableException;
}

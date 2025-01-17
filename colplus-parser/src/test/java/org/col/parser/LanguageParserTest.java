package org.col.parser;

import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.vocab.Language;
import org.junit.Test;

import static org.col.api.vocab.Language.byCode;
/**
 *
 */
public class LanguageParserTest extends ParserTestBase<Language> {

  public LanguageParserTest() {
    super(LanguageParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse("deu", "de");
    assertParse("deu", "deu");
    assertParse("deu", "german");
    assertParse("deu", "deutsch");
    assertParse("deu", "GER");
    assertParse("eng", "en");
    assertParse("ceb", "visayan");
    assertParse("ceb", "Ormocanon");
    assertParse("ceb", "Cebuano");
  
    for (String x : new String[]{"Limburgan", "Limburger", "Limburgish", "Lim", "li"}) {
      assertParse("lim", x);
    }
    
    assertUnparsable("unknown");
    assertUnparsable("zz");
  }
  
  private void assertParse(String expected, String input) throws UnparsableException {
    assertParse(byCode(expected), input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}
package org.col.api.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import org.col.api.model.CslData;
import org.col.api.vocab.CSLRefType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CslArrayMismatchHandlerTest {
  /**
   * Avoid failing when string properties are given as arrays
   */
  @Test
  public void jacksonDeserde() throws IOException {
    InputStream in = ClassLoader.getSystemResourceAsStream("reference.json");
    TypeReference<List<CslData>> cslType = new TypeReference<List<CslData>>(){};
    List<CslData> refs = ApiModule.MAPPER.readValue(in, cslType);
    
    CslData r = refs.get(0);
    assertEquals("The Global Genome Biodiversity Network (GGBN) Data Standard specification", r.getTitle());
    assertEquals("GGBN Standard", r.getTitleShort());
    assertEquals("10.1093/database/baw125", r.getDOI());
    assertEquals("http://dx.doi.org/10.1093/database/baw125", r.getURL());
    assertEquals("1758-0463", r.getISSN());
    assertEquals(CSLRefType.ARTICLE_JOURNAL, r.getType());
  
    r = refs.get(1);
    // should not fail
    assertNull(r.getType());
  }
}
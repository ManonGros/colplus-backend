package org.col.common.tax;

import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MisappliedNameMatcherTest {
  Name n;
  NameAccordingTo nat;

  @Before
  public void init() {
    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    nat = new NameAccordingTo(n, null);
  }

  @Test
  public void isMisappliedName() {
    nonMisapplied(null);
    nonMisapplied("Markus");
    nonMisapplied("s.str.");
    nonMisapplied("s.l.");
    nonMisapplied("sensu lato");

    misapplied("sensu auct. non Döring 2189");
    misapplied("sensu auctorum");
    misapplied("auct");
    misapplied("auct.");
    misapplied("auct Döring 2189");
    misapplied("auct nec Döring 2189");
    misapplied("nec Döring 2189");
  }

  private void nonMisapplied(String accordingTo) {
    nat.setAccordingTo(accordingTo);
    assertFalse(MisappliedNameMatcher.isMisappliedName(nat));
  }

  private void misapplied(String accordingTo) {
    nat.setAccordingTo(accordingTo);
    assertTrue(MisappliedNameMatcher.isMisappliedName(nat));
  }
}
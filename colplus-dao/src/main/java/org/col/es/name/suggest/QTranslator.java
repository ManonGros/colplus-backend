package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.dsl.AutoCompleteQuery;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.DisMaxQuery;
import org.col.es.dsl.PrefixQuery;
import org.col.es.dsl.Query;
import org.col.es.dsl.TermQuery;
import org.col.es.model.NameStrings;

import static org.col.es.model.NameStrings.tokenize;

class QTranslator {

  private static final int MAX_NGRAM_SIZE = 10; // see es-settings.json
  private static final float BASE_BOOST = 1.0F;

  private final NameSuggestRequest request;

  private final String q;
  private final NameStrings strings;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
    this.q = request.getQ().trim().toLowerCase();
    this.strings = new NameStrings(q);
  }

  Query getScientificNameQuery() {
    if (request.isEpithetSensitive()) {
      return getSimpleQuery();
    }
    Query advancedQuery = getAdvancedQuery();
    if (advancedQuery == null) {
      return getSimpleQuery();
    }
    /*
     * N.B. even when using the "advanced" query mechanism, we still combine it with the simple query mechanism. The simple
     * query is likely to yield more results, but the advanced mechanism is better and probably even more efficient if the
     * user is typing a binomial or trinomial in the search box (as will happen often). So we give the advanced query
     * mechanism a strong boost compared to the simple query mechanism, but still don't ignore the results produced by the
     * latter.
     */
    return new DisMaxQuery()
        .subquery(advancedQuery)
        .subquery(getSimpleQuery());
  }

  Query getVernacularNameQuery() {
    return new AutoCompleteQuery("vernacularNames", this.q, BASE_BOOST);
  }

  Query getSimpleQuery() {
    return new AutoCompleteQuery("nameStrings.scientificNameWN", strings.getScientificNameWN(), BASE_BOOST);
  }

  private Query getAdvancedQuery() {
    switch (tokenize(q).length) {
      case 1: // Compare the search phrase with genus, specific and infraspecific epithet
        return new BoolQuery()
            .should(getGenusQuery())
            .should(getSpecificEpithetQuery())
            .should(getInfraspecificEpithetQuery())
            .boost(BASE_BOOST * 1.1F);
      case 2: // match 1st term against genus and 2nd against either specific or infraspecific epithet
        return new BoolQuery()
            .must(getGenusQuery())
            .must(new BoolQuery()
                .should(getSpecificEpithetQuery())
                .should(getInfraspecificEpithetQuery()))
            .boost(BASE_BOOST * 1.5F);
      case 3:
        return new BoolQuery()
            .must(getGenusQuery())
            .must(getSpecificEpithetQuery())
            .must(getInfraspecificEpithetQuery())
            .boost(BASE_BOOST * 2); // that's super duper almost guaranteed to be bingo
      default:
        return null;
    }
  }

  private Query getGenusQuery() {
    if (strings.getGenus().length() == 1) {
      return new TermQuery("nameStrings.genusLetter", strings.getGenus());
    }
    if (strings.getGenusWN() == null) { // normalized version does not differ from the original string
      return compare("nameStrings.genus", strings.getGenus());
    }
    return compare("nameStrings.genusWN", strings.getGenusWN());
  }

  private Query getSpecificEpithetQuery() {
    if (strings.getSpecificEpithetSN() == null) {
      return compare("nameStrings.specificEpithet", strings.getSpecificEpithet());
    }
    return compare("nameStrings.specificEpithetSN", strings.getSpecificEpithetSN());
  }

  private Query getInfraspecificEpithetQuery() {
    if (strings.getInfraspecificEpithetSN() == null) {
      return compare("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet());
    }
    return compare("nameStrings.infraspecificEpithetSN", strings.getInfraspecificEpithetSN());
  }

  private static Query compare(String field, String value) {
    if (value.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, value);
    }
    return new AutoCompleteQuery(field, value);
  }

}
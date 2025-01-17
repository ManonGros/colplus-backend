package org.col.es.name.suggest;

import org.col.api.search.NameSuggestRequest;
import org.col.es.query.BoolQuery;
import org.col.es.query.Query;

import static org.col.es.name.QTranslationUtils.getScientificNameQuery;
import static org.col.es.name.QTranslationUtils.getVernacularNameQuery;

class QTranslator {

  static final String SN_QUERY_NAME = "sn";
  static final String VN_QUERY_NAME = "vn";

  private final NameSuggestRequest request;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
  }

  Query translate() {
    if (request.suggestVernaculars()) {
      return new BoolQuery()
          .should(getScientificNameQuery(request.getQ()).withName(SN_QUERY_NAME))
          .should(getVernacularNameQuery(request.getQ()).withName(VN_QUERY_NAME));
    }
    return getScientificNameQuery(request.getQ()).withName(SN_QUERY_NAME);
  }

}

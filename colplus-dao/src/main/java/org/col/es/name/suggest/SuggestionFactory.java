package org.col.es.name.suggest;

import java.util.List;

import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestion;
import org.col.es.model.NameUsageDocument;
import org.col.es.response.SearchHit;

class SuggestionFactory {

  private final VernacularNameMatcher matcher;

  SuggestionFactory(NameSuggestRequest request) {
    if (request.suggestVernaculars()) {
      this.matcher = new VernacularNameMatcher(request);
    } else {
      // matcher is not going to be used
      this.matcher = null;
    }
  }

  NameSuggestion createSuggestion(SearchHit<NameUsageDocument> hit, boolean isVernacularName) {
    NameSuggestion suggestion = new NameSuggestion();
    suggestion.setScore(hit.getScore());
    suggestion.setVernacularName(isVernacularName);
    NameUsageDocument doc = hit.getSource();
    if (isVernacularName) {
      List<String> names = hit.getSource().getVernacularNames();
      suggestion.setMatch(matcher.getMatch(names));
    } else {
      suggestion.setMatch(doc.getScientificName());
    }
    if (doc.getStatus() != null && doc.getStatus().isSynonym()) {
      suggestion.setAcceptedName(doc.getAcceptedName());
    }
    suggestion.setUsageId(doc.getUsageId());
    suggestion.setNomCode(doc.getNomCode());
    suggestion.setRank(doc.getRank());
    suggestion.setStatus(doc.getStatus());
    return suggestion;
  }

}

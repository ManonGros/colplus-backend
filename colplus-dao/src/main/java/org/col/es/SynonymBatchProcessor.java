package org.col.es;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.col.api.model.SimpleName;
import org.col.api.model.Synonym;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.BoolQuery;
import org.col.es.query.CollapsibleList;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.SortField;
import org.col.es.query.TermQuery;
import org.col.es.query.TermsQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toMap;

/**
 * Collects synonyms retrieved from Postgres until a trashold is reached and then enriches them with classifications before insert them into
 * Elasticsearch.
 */
class SynonymBatchProcessor implements Consumer<List<NameUsageWrapper>>, AutoCloseable {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SynonymBatchProcessor.class);

  /*
   * 655536 is the absolute maximum number of terms in a terms query, but that may take up too much memory.
   */
  private static final int LOOKUP_BATCH_SIZE = 8192;

  private final NameUsageIndexer indexer;
  private final int datasetKey;

  private final List<String> taxonIds = new ArrayList<>(LOOKUP_BATCH_SIZE);
  private final List<NameUsageWrapper> collected = new ArrayList<>(LOOKUP_BATCH_SIZE);

  private String prevTaxonId = "";

  SynonymBatchProcessor(NameUsageIndexer indexer, int datasetKey) {
    this.indexer = indexer;
    this.datasetKey = datasetKey;
  }

  @Override
  public void accept(List<NameUsageWrapper> batch) {
    try {
      for (NameUsageWrapper nuw : batch) {
        collected.add(nuw);
        String taxonId = ((Synonym) nuw.getUsage()).getAccepted().getId();
        if (taxonId.equals(prevTaxonId)) {
          // Assumption: synonyms ordered by accepted name id
          continue;
        }
        prevTaxonId = taxonId;
        taxonIds.add(taxonId);
        if (taxonIds.size() == LOOKUP_BATCH_SIZE) {
          flush();
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws Exception {
    if (collected.size() != 0) {
      flush();
    }
  }

  private void flush() throws IOException {
    // Create a lookup table mapping taxon ids to classifications.
    Map<String, List<SimpleName>> lookups = loadTaxa()
        .stream()
        .collect(toMap(EsNameUsage::getUsageId, NameUsageTransfer::extractClassifiction));
    collected.forEach(nuw -> {
      String taxonId = ((Synonym) nuw.getUsage()).getAccepted().getId();
      nuw.setClassification(lookups.get(taxonId));
    });
    indexer.accept(collected);
    taxonIds.clear();
    collected.clear();
    prevTaxonId = "";
  }

  private List<EsNameUsage> loadTaxa() throws IOException {
    NameUsageSearchService svc = new NameUsageSearchService(indexer.getEsClient());
    BoolQuery query = new BoolQuery()
        .filter(new TermQuery("datasetKey", datasetKey))
        .filter(new TermsQuery("usageId", taxonIds));
    EsSearchRequest esr = EsSearchRequest.emptyRequest();
    esr.setQuery(new ConstantScoreQuery(query));
    esr.setSort(CollapsibleList.of(SortField.DOC));
    esr.setSize(LOOKUP_BATCH_SIZE);
    return svc.getDocuments(indexer.getIndexName(), esr);
  }

}

package org.col.resources;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.codahale.metrics.annotation.Timed;

import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.InvalidQueryException;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.name.suggest.NameSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameSearchResource.class);

  private final NameUsageSearchService searchService;
  private final NameSuggestionService suggestService;

  public NameSearchResource(NameUsageSearchService search, NameSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  @Timed
  @Path("/search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameSearchRequest query,
      @Valid @BeanParam Page page,
      @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    return searchService.search(query, page);
  }

  @GET
  @Timed
  @Path("/suggest")
  public NameSuggestResponse suggest(@BeanParam NameSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }

}

package org.col.es.name.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.col.api.TestEntityGenerator;
import org.col.api.model.BareName;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.VernacularName;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.Issue;
import org.col.es.EsReadTestBase;
import org.col.es.name.NameUsageWrapperConverter;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsUtil.insert;
import static org.col.es.EsUtil.refreshIndex;
import static org.junit.Assert.assertEquals;

public class NameSearchServiceTest extends EsReadTestBase {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameSearchServiceTest.class);

  private static RestClient client;
  private static NameUsageSearchServiceEs svc;

  @BeforeClass
  public static void init() {
    client = esSetupRule.getEsClient();
    svc = new NameUsageSearchServiceEs(indexName, esSetupRule.getEsClient());
  }

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testQuery1() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE, Issue.ACCEPTED_NAME_MISSING);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, converter.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(3, result.getResult().size());
  }

  @Test
  public void testQuery2() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents with an issue of either ACCEPTED_NAME_MISSING or ACCORDING_TO_DATE_INVALID
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, converter.toDocument(nuw5));

    // No match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw7));

    // No match
    NameUsageWrapper nuw8 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw8.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.DOUBTFUL_NAME));
    insert(client, indexName, converter.toDocument(nuw8));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void testQuery3() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents with an issue of any of ACCEPTED_NAME_MISSING, ACCORDING_TO_DATE_INVALID, BASIONYM_ID_INVALID
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE,
        Issue.ACCEPTED_NAME_MISSING,
        Issue.ACCORDING_TO_DATE_INVALID,
        Issue.BASIONYM_ID_INVALID);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING));
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCORDING_TO_DATE_INVALID));
    insert(client, indexName, converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(null);
    insert(client, indexName, converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw5.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, converter.toDocument(nuw5));

    // Match
    NameUsageWrapper nuw6 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw6.setIssues(EnumSet.of(Issue.CITATION_UNPARSED, Issue.BASIONYM_ID_INVALID));
    insert(client, indexName, converter.toDocument(nuw6));

    // No match
    NameUsageWrapper nuw7 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw7.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw7));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete1() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search
    NameSearchRequest query = new NameSearchRequest();
    query.setHighlight(false);
    query.setQ("UNLIKE");

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw2));

    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw3));

    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, query, new Page());

    assertEquals(4, result.getResult().size());
  }

  @Test
  public void autocomplete2() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    // Only search in authorship field
    nsr.setContent(EnumSet.of(NameSearchRequest.SearchContent.AUTHORSHIP));
    nsr.setQ("UNLIKE");

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    List<String> vernaculars = Arrays.asList("AN UNLIKELY NAME");
    nuw1.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw1));

    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("ANOTHER NAME", "AN UNLIKELY NAME");
    nuw2.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw2));

    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("YET ANOTHER NAME", "ANOTHER NAME", "AN UNLIKELY NAME");
    nuw3.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw3));

    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("it's unlike capital case");
    nuw4.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw4));

    // No match
    NameUsageWrapper nuw5 = TestEntityGenerator.newNameUsageTaxonWrapper();
    vernaculars = Arrays.asList("LIKE IT OR NOT");
    nuw5.setVernacularNames(create(vernaculars));
    insert(client, indexName, converter.toDocument(nuw5));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(0, result.getResult().size());
  }

  @Test
  public void testIsNull() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.IS_NULL);

    // Match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw1));
    // No match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw2));
    // Match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, converter.toDocument(nuw3));
    // No match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, converter.toDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testIsNotNull() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Define search condition
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.addFilter(NameSearchParameter.ISSUE, NameSearchRequest.IS_NOT_NULL);

    // No match
    NameUsageWrapper nuw1 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw1.setIssues(EnumSet.noneOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw1));
    // Match
    NameUsageWrapper nuw2 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw2.setIssues(EnumSet.allOf(Issue.class));
    insert(client, indexName, converter.toDocument(nuw2));
    // No match
    NameUsageWrapper nuw3 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw3.setIssues(null);
    insert(client, indexName, converter.toDocument(nuw3));
    // Match
    NameUsageWrapper nuw4 = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw4.setIssues(EnumSet.of(Issue.CITATION_UNPARSED));
    insert(client, indexName, converter.toDocument(nuw4));

    refreshIndex(client, indexName);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(2, result.getResult().size());
  }

  @Test
  public void testNameFieldsQuery1() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testNameFieldsQuery2() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw2));

    // No match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw3));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw4));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw4);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testNameFieldsQuery3() throws IOException {
    NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

    // Find all documents where the uninomial field is not empty
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.addFilter(NameSearchParameter.FIELD, "uninomial", "remarks", "specific_epithet");

    // Match
    Name n = new Name();
    n.setUninomial("laridae");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw1));

    // Match
    n = new Name();
    n.setUninomial("parus");
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw2));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setSpecificEpithet("major");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw3));

    // No Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw4));

    // Match
    n = new Name();
    n.setUninomial(null);
    n.setGenus("parus");
    n.setRemarks("A bird");
    bn = new BareName(n);
    NameUsageWrapper nuw5 = new NameUsageWrapper(bn);
    insert(client, indexName, converter.toDocument(nuw5));

    refreshIndex(client, indexName);

    List<NameUsageWrapper> expected = Arrays.asList(nuw1, nuw2, nuw3, nuw5);

    ResultPage<NameUsageWrapper> result = svc.search(indexName, nsr, new Page());

    assertEquals(expected, result.getResult());

  }

  @Test
  public void testWithBigQ() {
    NameSearchRequest query = new NameSearchRequest();
    query.setHighlight(false);
    query.setQ("ABCDEFGHIJKLMNOPQRSTUVW");
    List<NameUsageWrapper> documents = testWithBigQ_data();
    index(documents);
    NameSearchResponse response = search(query);
    assertEquals(2, response.getResult().size());
  }

  private List<NameUsageWrapper> testWithBigQ_data() {

    List<NameUsageWrapper> usages = createNameUsages(4);
    
    // Match on scientific name
    usages.get(0).getUsage().getName().setSpecificEpithet("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    
    // Match on vernacular name
    VernacularName vn = new VernacularName();
    vn.setName("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    usages.get(1).setVernacularNames(Arrays.asList(vn));

    // No match (missing 'W')
    usages.get(2).getUsage().getName().setSpecificEpithet("ABCDEFGHIJKLMNOPQRSTUV");

    // No match (missing 'A')
    usages.get(3).getUsage().getName().setSpecificEpithet("BCDEFGHIJKLMNOPQRSTUVW");
    
    return usages;

  }

  // Issue #207
  @Test
  public void testWithSmthii__1() {
    NameSearchRequest query = new NameSearchRequest();
    query.setHighlight(false);
    query.setQ("Smithi");
    index(testWithSmthii_data());
    // Expect all to come back
    NameSearchResponse result = search(query);
    assertEquals(testWithSmthii_data(), result.getResult());
  }

  @Test
  public void testWithSmthii__2() {
    NameSearchRequest nsr = new NameSearchRequest();
    nsr.setHighlight(false);
    nsr.setQ("Smithii");
    index(testWithSmthii_data());
    // Expect all to come back
    NameSearchResponse result = search(nsr);
    assertEquals(testWithSmthii_data(), result.getResult());
  }

  private static List<NameUsageWrapper> testWithSmthii_data() {
    Name n = new Name();
    n.setSpecificEpithet("Smithii");
    BareName bn = new BareName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("Smithi");
    bn = new BareName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("SmithiiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw3 = new NameUsageWrapper(bn);

    n = new Name();
    n.setSpecificEpithet("SmithiFooBar");
    bn = new BareName(n);
    NameUsageWrapper nuw4 = new NameUsageWrapper(bn);

    return Arrays.asList(nuw1, nuw2, nuw3, nuw4);
  }

  private static List<VernacularName> create(List<String> names) {
    return names.stream().map(n -> {
      VernacularName vn = new VernacularName();
      vn.setName(n);
      return vn;
    }).collect(Collectors.toList());
  }
}

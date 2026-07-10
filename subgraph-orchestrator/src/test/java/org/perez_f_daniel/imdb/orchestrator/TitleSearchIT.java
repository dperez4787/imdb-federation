package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.ADULT;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.BB;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.GOT;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.NOIR;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.SHORT;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.common.testsupport.MongoCommandCounter;
import org.perez_f_daniel.imdb.common.testsupport.MongoCounterTestConfig;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest
@Import(MongoCounterTestConfig.class)
class TitleSearchIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    rebuild.run(List.of(Step.values()));
    // warm the titleTypes cache so command counts below are pure search traffic
    tconsts("{ searchTitles { items { tconst } } }");
    counter.reset();
  }

  private List<String> tconsts(String query) {
    List<Map<String, Object>> items = dgs.executeAndExtractJsonPath(query, "data.searchTitles.items");
    return items.stream().map(i -> (String) i.get("tconst")).toList();
  }

  @Test
  void genrePeriodRatingSort() {
    List<String> r = tconsts("""
        { searchTitles(filter: {genresAny: ["Drama"], titleTypes: ["tvSeries"],
                                startYearFrom: 2005, startYearTo: 2015},
                       sort: RATING_DESC) { items { tconst } } }""");
    assertThat(r).containsExactly(BB, GOT);
  }

  @Test
  void emptyFilterBrowsesByPopularityExcludingAdult() {
    List<String> r = tconsts("{ searchTitles { items { tconst } } }");
    assertThat(r).containsExactly(GOT, BB, NOIR, SHORT);
    assertThat(counter.count("aggregate")).as("items-only = one aggregate").isEqualTo(1);
  }

  @Test
  void explicitPopularitySortMatchesDefault() {
    assertThat(tconsts("{ searchTitles(sort: POPULARITY_DESC) { items { tconst } } }"))
        .containsExactly(GOT, BB, NOIR, SHORT);
  }

  @Test
  void withPeopleAllVsAny() {
    assertThat(tconsts("""
        { searchTitles(filter: {withPeople: ["nm0000001", "nm0000002"], peopleMode: ALL})
          { items { tconst } } }"""))
        .containsExactly(GOT);
    assertThat(tconsts("""
        { searchTitles(filter: {withPeople: ["nm0000001", "nm0000002"], peopleMode: ANY})
          { items { tconst } } }"""))
        .containsExactly(GOT, BB);
  }

  @Test
  void textQueryAndPrefixAutocomplete() {
    assertThat(tconsts("{ searchTitles(filter: {query: \"Breaking\"}) { items { tconst } } }"))
        .containsExactly(BB);
    assertThat(tconsts("{ searchTitles(filter: {titlePrefix: \"brea\"}) { items { tconst } } }"))
        .containsExactly(BB);
  }

  @Test
  void adultTitlesRequireOptIn() {
    String query = """
        { searchTitles(filter: {genresAny: ["Drama"], startYearFrom: 2009, startYearTo: 2011%s})
          { items { tconst } } }""";
    assertThat(tconsts(query.formatted(""))).containsExactly(GOT);
    assertThat(tconsts(query.formatted(", includeAdult: true"))).containsExactly(GOT, ADULT);
  }

  @Test
  void lazyTotalSharesOneCountQuery() {
    Map<String, Object> result = dgs.executeAndExtractJsonPath("""
        { searchTitles(filter: {genresAny: ["Drama"]}) { total totalIsCapped items { tconst } } }""",
        "data.searchTitles");
    assertThat(result).containsEntry("total", 2).containsEntry("totalIsCapped", false);
    assertThat((List<?>) result.get("items")).hasSize(2);
    assertThat(counter.count("aggregate"))
        .as("items + (total, totalIsCapped memoized) = exactly two aggregates")
        .isEqualTo(2);
  }

  @Test
  void unknownGenreMatchesNothing() {
    Map<String, Object> result = dgs.executeAndExtractJsonPath(
        "{ searchTitles(filter: {genresAny: [\"Nope\"]}) { total items { tconst } } }",
        "data.searchTitles");
    assertThat(result).containsEntry("total", 0);
    assertThat((List<?>) result.get("items")).isEmpty();
  }

  @Test
  void pagingIsDeterministicAndDisjoint() {
    List<String> page1 = tconsts("{ searchTitles(limit: 2) { items { tconst } } }");
    List<String> page2 = tconsts("{ searchTitles(limit: 2, offset: 2) { items { tconst } } }");
    assertThat(page1).containsExactly(GOT, BB);
    assertThat(page2).containsExactly(NOIR, SHORT);
    assertThat(tconsts("{ searchTitles(limit: 2) { items { tconst } } }")).isEqualTo(page1);
  }
}

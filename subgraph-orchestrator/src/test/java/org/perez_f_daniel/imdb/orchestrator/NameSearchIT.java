package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.perez_f_daniel.imdb.orchestrator.OrchestratorFixtures.GOT;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest
class NameSearchIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    rebuild.run(List.of(Step.values()));
  }

  private List<String> nconsts(String query) {
    List<Map<String, Object>> items = dgs.executeAndExtractJsonPath(query, "data.searchNames.items");
    return items.stream().map(i -> (String) i.get("nconst")).toList();
  }

  @Test
  void actorsInATitle() {
    assertThat(nconsts("""
        { searchNames(filter: {inTitles: ["%s"]}) { items { nconst } } }""".formatted(GOT)))
        .containsExactly("nm0000001", "nm0000002"); // 1 credit each -> _id tiebreak
  }

  @Test
  void unscopedPopularityRanking() {
    assertThat(nconsts("{ searchNames(sort: POPULARITY_DESC) { items { nconst } } }"))
        .containsExactly("nm0000002", "nm0000001", "nm0000003", "nm0000004");
  }

  @Test
  void unscopedRelevanceDefaultsToPopularity() {
    assertThat(nconsts("{ searchNames { items { nconst } } }"))
        .containsExactly("nm0000002", "nm0000001", "nm0000003", "nm0000004");
  }

  @Test
  void actorsFromGenreInPeriod() {
    Map<String, Object> result = dgs.executeAndExtractJsonPath("""
        { searchNames(filter: {inGenres: ["Film-Noir"], activeFrom: 1940, activeTo: 1955,
                               categories: ["actor"]})
          { titleCandidatesCapped items { nconst } } }""",
        "data.searchNames");
    assertThat((List<Map<String, Object>>) result.get("items"))
        .extracting(i -> i.get("nconst")).containsExactly("nm0000003");
    assertThat(result).containsEntry("titleCandidatesCapped", false);
  }

  @Test
  void scopedPopularitySort() {
    // both GoT principals, sorted by materialized popularity: Cranston (3.9M) > Clarke (2M)
    assertThat(nconsts("""
        { searchNames(filter: {inTitles: ["%s"]}, sort: POPULARITY_DESC) { items { nconst } } }"""
        .formatted(GOT)))
        .containsExactly("nm0000002", "nm0000001");
  }

  @Test
  void degradedQueryFiltersJoinedCandidates() {
    assertThat(nconsts("""
        { searchNames(filter: {inTitles: ["%s"], query: "clarke"}) { items { nconst } } }"""
        .formatted(GOT)))
        .containsExactly("nm0000001");
  }

  @Test
  void textQueryUnscoped() {
    assertThat(nconsts("{ searchNames(filter: {query: \"Cranston\"}) { items { nconst } } }"))
        .containsExactly("nm0000002");
  }

  @Test
  void namePrefixAutocomplete() {
    assertThat(nconsts("{ searchNames(filter: {namePrefix: \"emi\"}) { items { nconst } } }"))
        .containsExactly("nm0000001");
  }

  @Test
  void professionAndBirthFilters() {
    assertThat(nconsts("""
        { searchNames(filter: {professions: ["actor"], bornFrom: 1900, bornTo: 1930})
          { items { nconst } } }"""))
        .containsExactly("nm0000003");
  }

  @Test
  void totalCountsDistinctPeople() {
    Map<String, Object> result = dgs.executeAndExtractJsonPath("""
        { searchNames(filter: {inTitles: ["%s"]}) { total totalIsCapped items { nconst } } }"""
        .formatted(GOT), "data.searchNames");
    assertThat(result).containsEntry("total", 2).containsEntry("totalIsCapped", false);
  }
}

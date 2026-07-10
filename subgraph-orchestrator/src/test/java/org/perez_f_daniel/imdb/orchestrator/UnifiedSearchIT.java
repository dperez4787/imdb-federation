package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest
class UnifiedSearchIT extends AbstractMongoIntegrationTest {

  private static final String QUERY = """
      query ($q: String!, $kinds: [SearchKind!]) {
        search(query: $q, kinds: $kinds, limit: 10) {
          __typename
          ... on Title { tconst }
          ... on Name { nconst }
        }
      }""";

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    // a person sharing a token with a title, to prove mixed-kind results
    mongo.insert(Document.parse("""
        {"nconst":"nm0000005","primaryName":"Carmencita Prado",
         "primaryProfession":"actress"}"""), "name_basics");
    rebuild.run(List.of(Step.values()));
  }

  private List<Map<String, Object>> hits(String q, String kinds) {
    return dgs.executeAndExtractJsonPath(QUERY, "data.search",
        kinds == null ? Map.of("q", q) : Map.of("q", q, "kinds", List.of(kinds)));
  }

  @Test
  void mixedKindsRankedTogether() {
    List<Map<String, Object>> r = hits("Carmencita", null);
    assertThat(r).extracting(h -> h.get("__typename"))
        .containsExactlyInAnyOrder("Title", "Name");
  }

  @Test
  void titleSearchFindsAndRanksByPopularityAmongTies() {
    List<Map<String, Object>> r = hits("Breaking Bad", null);
    assertThat(r.get(0)).containsEntry("__typename", "Title")
        .containsEntry("tconst", OrchestratorFixtures.BB);
  }

  @Test
  void nameOnlyKindFilter() {
    List<Map<String, Object>> r = hits("Carmencita", "NAME");
    assertThat(r).hasSize(1);
    assertThat(r.get(0)).containsEntry("__typename", "Name")
        .containsEntry("nconst", "nm0000005");
  }

  @Test
  void titleOnlyKindFilter() {
    List<Map<String, Object>> r = hits("Carmencita", "TITLE");
    assertThat(r).hasSize(1);
    assertThat(r.get(0)).containsEntry("__typename", "Title")
        .containsEntry("tconst", OrchestratorFixtures.SHORT);
  }

  @Test
  void personSearch() {
    List<Map<String, Object>> r = hits("Cranston", null);
    assertThat(r).hasSize(1);
    assertThat(r.get(0)).containsEntry("nconst", "nm0000002");
  }

  @Test
  void adultTitlesExcluded() {
    assertThat(hits("Adult Drama", null))
        .noneMatch(h -> OrchestratorFixtures.ADULT.equals(h.get("tconst")));
  }

  @Test
  void shortQueryRejected() {
    graphql.ExecutionResult result = dgs.execute(
        "{ search(query: \"a\") { __typename } }");
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0).getMessage()).contains("2 characters");
  }
}

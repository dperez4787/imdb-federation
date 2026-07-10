package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

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
class FacetsIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    rebuild.run(List.of(Step.values()));
  }

  @Test
  void vocabularyFacetsMaterializedWithCounts() {
    Map<String, Object> facets = dgs.executeAndExtractJsonPath("""
        { facets {
            genres { value count }
            titleTypes { value count }
            principalCategories { value count }
            professions { value count }
            akaRegions { value count }
            akaLanguages { value count }
        } }""", "data.facets");

    List<Map<String, Object>> genres = (List<Map<String, Object>>) facets.get("genres");
    assertThat(genres.get(0)).containsEntry("value", "Drama").containsEntry("count", 3);
    assertThat(genres).extracting(g -> g.get("value")).contains("Crime", "Film-Noir", "Short");

    assertThat((List<Map<String, Object>>) facets.get("principalCategories"))
        .containsExactly(Map.of("value", "actor", "count", 4), Map.of("value", "actress", "count", 1));
    assertThat((List<Map<String, Object>>) facets.get("titleTypes"))
        .extracting(t -> t.get("value"))
        .containsExactlyInAnyOrder("movie", "short", "tvSeries");
    assertThat((List<Map<String, Object>>) facets.get("professions"))
        .extracting(p -> p.get("value")).contains("actor", "actress", "producer");
    assertThat((List<Map<String, Object>>) facets.get("akaRegions"))
        .containsExactly(Map.of("value", "ES", "count", 1));
    assertThat((List<Map<String, Object>>) facets.get("akaLanguages"))
        .containsExactly(Map.of("value", "es", "count", 1));
  }

  @Test
  void contextualTitleFacetsReflectTheFilter() {
    List<Map<String, Object>> buckets = dgs.executeAndExtractJsonPath("""
        { searchTitles(filter: {genresAny: ["Drama"]}) {
            facets(dimensions: [GENRES, DECADES, RATING_BANDS]) {
              dimension values { value count } } } }""",
        "data.searchTitles.facets");

    Map<String, List<Map<String, Object>>> byDim = new java.util.HashMap<>();
    for (Map<String, Object> b : buckets) {
      byDim.put((String) b.get("dimension"), (List<Map<String, Object>>) b.get("values"));
    }
    // Drama + non-adult = GoT + BB
    assertThat(byDim.get("GENRES").get(0))
        .containsEntry("value", "Drama").containsEntry("count", 2);
    assertThat(byDim.get("DECADES"))
        .containsExactly(Map.of("value", "2010", "count", 1), Map.of("value", "2000", "count", 1));
    assertThat(byDim.get("RATING_BANDS"))
        .containsExactly(Map.of("value", "9", "count", 2));
  }

  @Test
  void contextualNameFacetsWorkOnScopedSearch() {
    List<Map<String, Object>> buckets = dgs.executeAndExtractJsonPath("""
        { searchNames(filter: {inTitles: ["%s"]}) {
            facets(dimensions: [PROFESSIONS, BIRTH_DECADES]) {
              dimension values { value count } } } }""".formatted(OrchestratorFixtures.GOT),
        "data.searchNames.facets");

    Map<String, List<Map<String, Object>>> byDim = new java.util.HashMap<>();
    for (Map<String, Object> b : buckets) {
      byDim.put((String) b.get("dimension"), (List<Map<String, Object>>) b.get("values"));
    }
    assertThat(byDim.get("PROFESSIONS").get(0))
        .containsEntry("value", "producer").containsEntry("count", 2);
    assertThat(byDim.get("BIRTH_DECADES"))
        .containsExactly(Map.of("value", "1980", "count", 1), Map.of("value", "1950", "count", 1));
  }

  @Test
  void contextualNameFacetsWorkUnscoped() {
    List<Map<String, Object>> buckets = dgs.executeAndExtractJsonPath("""
        { searchNames(filter: {professions: ["actor"]}) {
            facets(dimensions: [BIRTH_DECADES]) { dimension values { value count } } } }""",
        "data.searchNames.facets");
    // actors: Cranston (1956), Noir Star (1920); No Votes Person has no professions
    assertThat((List<Map<String, Object>>) buckets.get(0).get("values"))
        .containsExactly(Map.of("value", "1950", "count", 1), Map.of("value", "1920", "count", 1));
  }
}

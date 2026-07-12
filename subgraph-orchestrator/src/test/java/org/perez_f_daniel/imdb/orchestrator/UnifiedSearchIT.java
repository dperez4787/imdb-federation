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
    // punctuation in the source title -> clean tokens after rebuild
    mongo.insert(Document.parse("""
        {"tconst":"tt9000001","titleType":"movie","primaryTitle":"Monsters, Inc.",
         "originalTitle":"Monsters, Inc.","isAdult":0,"startYear":2001,
         "runtimeMinutes":92,"genres":"Animation,Comedy"}"""), "title_basics");
    // shares "game"/"of" with Game of Thrones at a fraction of the votes
    mongo.insert(Document.parse("""
        {"tconst":"tt9000002","titleType":"movie","primaryTitle":"Game of Stones",
         "originalTitle":"Game of Stones","isAdult":0,"startYear":2013,
         "runtimeMinutes":45,"genres":"Documentary"}"""), "title_basics");
    // shares its only token with Bryan Cranston (popularity 3.9M vs 1k votes)
    mongo.insert(Document.parse("""
        {"tconst":"tt9000003","titleType":"movie","primaryTitle":"Cranston",
         "originalTitle":"Cranston","isAdult":0,"startYear":1999,
         "runtimeMinutes":90,"genres":"Drama"}"""), "title_basics");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt9000002\",\"averageRating\":6.5,\"numVotes\":100}"), "title_ratings");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt9000003\",\"averageRating\":7.0,\"numVotes\":1000}"), "title_ratings");
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
  void popularityRanksAcrossKinds() {
    // Bryan Cranston (knownFor votes 3.9M) outranks the "Cranston" title (1k votes)
    List<Map<String, Object>> r = hits("Cranston", null);
    assertThat(r).hasSize(2);
    assertThat(r.get(0)).containsEntry("nconst", "nm0000002");
    assertThat(r.get(1)).containsEntry("tconst", "tt9000003");
  }

  @Test
  void popularityRanksWithinTitles() {
    List<Map<String, Object>> r = hits("game of", "TITLE");
    assertThat(r).extracting(h -> h.get("tconst"))
        .containsExactly(OrchestratorFixtures.GOT, "tt9000002");
  }

  @Test
  void everyTokenMustMatch() {
    assertThat(hits("Breaking Thrones", null)).isEmpty();
  }

  @Test
  void trailingTokenPrefixMatches() {
    List<Map<String, Object>> r = hits("Game of Thro", "TITLE");
    assertThat(r).hasSize(1);
    assertThat(r.get(0)).containsEntry("tconst", OrchestratorFixtures.GOT);
  }

  @Test
  void shortTrailingTokenStaysExact() {
    // "ba" is under the 3-char prefix minimum -> exact token, no match
    assertThat(hits("Breaking Ba", null)).isEmpty();
  }

  @Test
  void loneTokenNeverPrefixMatches() {
    assertThat(hits("Cranst", null)).isEmpty();
  }

  @Test
  void punctuationAndCaseFoldAway() {
    List<Map<String, Object>> r = hits("monsters inc", "TITLE");
    assertThat(r).hasSize(1);
    assertThat(r.get(0)).containsEntry("tconst", "tt9000001");
    assertThat(hits("MONSTERS, INC.", "TITLE")).isEqualTo(r);
  }

  @Test
  void allPunctuationQueryReturnsEmpty() {
    assertThat(hits("!!", null)).isEmpty();
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

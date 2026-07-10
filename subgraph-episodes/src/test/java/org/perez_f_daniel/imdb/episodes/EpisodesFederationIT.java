package org.perez_f_daniel.imdb.episodes;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.common.testsupport.MongoCommandCounter;
import org.perez_f_daniel.imdb.common.testsupport.MongoCounterTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest
@Import(MongoCounterTestConfig.class)
class EpisodesFederationIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_episode");
    // Game of Thrones: 2 seasons x 2 episodes, inserted out of order
    int i = 0;
    for (int[] se : new int[][] {{2, 2}, {1, 1}, {2, 1}, {1, 2}}) {
      mongo.insert(new Document(Map.of(
          "tconst", "tt094494" + (++i), "parentTconst", "tt0944947",
          "seasonNumber", se[0], "episodeNumber", se[1])), "title_episode");
    }
    // one episode row referencing its series, for Title.episode
    mongo.insert(new Document(Map.of(
        "tconst", "tt0959621", "parentTconst", "tt0903747",
        "seasonNumber", 1, "episodeNumber", 1)), "title_episode");
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
  }

  @Test
  void episodeFieldResolvesRowAndSeriesStub() {
    Map<String, Object> episode = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) {
            ... on Title { episode { seasonNumber episodeNumber series { tconst } } }
          }
        }""",
        "data._entities[0].episode",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt0959621"))));
    assertThat(episode).containsEntry("seasonNumber", 1).containsEntry("episodeNumber", 1);
    assertThat((Map<String, Object>) episode.get("series")).containsEntry("tconst", "tt0903747");
  }

  @Test
  void episodesArePagedSortedAndBatchedIntoOneAggregate() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) {
            ... on Title { tconst episodes(limit: 2, offset: 1) { tconst } }
          }
        }""",
        "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt0944947"),
            Map.of("__typename", "Title", "tconst", "tt0903747"))));
    // sorted order is s1e1(tconst ...2), s1e2(...4), s2e1(...3), s2e2(...1); offset 1 limit 2
    assertThat((List<Map<String, Object>>) entities.get(0).get("episodes"))
        .extracting(t -> t.get("tconst"))
        .containsExactly("tt0944944", "tt0944943");
    assertThat((List<Map<String, Object>>) entities.get(1).get("episodes"))
        .extracting(t -> t.get("tconst"))
        .containsExactly();
    assertThat(counter.count("aggregate"))
        .as("both parents' pages must ride one aggregation")
        .isEqualTo(1);
  }

  @Test
  void episodesOfSeriesRootQueryPaginates() {
    List<Map<String, Object>> titles = dgs.executeAndExtractJsonPath(
        "{ episodesOfSeries(parentTconst: \"tt0944947\", limit: 3) { tconst } }",
        "data.episodesOfSeries");
    assertThat(titles).extracting(t -> t.get("tconst"))
        .containsExactly("tt0944942", "tt0944944", "tt0944943");
  }

  @Test
  void nonSeriesTitleGetsEmptyEpisodes() {
    List<Object> episodes = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) { ... on Title { episodes { tconst } } }
        }""",
        "data._entities[0].episodes",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt9999999"))));
    assertThat(episodes).isEmpty();
  }
}

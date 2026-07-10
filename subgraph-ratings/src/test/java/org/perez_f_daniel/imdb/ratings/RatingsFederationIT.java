package org.perez_f_daniel.imdb.ratings;

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
class RatingsFederationIT extends AbstractMongoIntegrationTest {

  private static final String ENTITIES_QUERY = """
      query ($reps: [_Any!]!) {
        _entities(representations: $reps) {
          ... on Title { tconst rating { averageRating numVotes } }
        }
      }""";

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_ratings");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt0944947\",\"averageRating\":9.2,\"numVotes\":2216000}"), "title_ratings");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt0903747\",\"averageRating\":9.5,\"numVotes\":1900000}"), "title_ratings");
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
  }

  @Test
  void ratingsResolveInOneBatchedFind() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt0944947"),
            Map.of("__typename", "Title", "tconst", "tt0903747"))));
    assertThat(entities).hasSize(2);
    Map<String, Object> got = (Map<String, Object>) entities.get(0).get("rating");
    assertThat(got).containsEntry("averageRating", 9.2).containsEntry("numVotes", 2216000);
    assertThat(counter.count("find")).isEqualTo(1);
  }

  @Test
  void titleWithoutRatingsRowHasNullRating() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt9999999"))));
    assertThat(entities.get(0)).containsEntry("tconst", "tt9999999");
    assertThat(entities.get(0).get("rating")).isNull();
  }
}

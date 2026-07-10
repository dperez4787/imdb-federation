package org.perez_f_daniel.imdb.titles;

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

/** Proves the subgraph speaks Apollo federation: _service.sdl and batched _entities. */
@SpringBootTest
@Import(MongoCounterTestConfig.class)
class TitlesFederationIT extends AbstractMongoIntegrationTest {

  private static final String ENTITIES_QUERY = """
      query ($reps: [_Any!]!) {
        _entities(representations: $reps) { ... on Title { tconst primaryTitle } }
      }""";

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_basics");
    for (String[] t : new String[][] {
        {"tt0944947", "Game of Thrones"}, {"tt0903747", "Breaking Bad"}, {"tt0141842", "The Sopranos"}}) {
      mongo.insert(new Document(Map.of("tconst", t[0], "primaryTitle", t[1], "titleType", "tvSeries")),
          "title_basics");
    }
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    // graphql-java's printer emits "fields : \"tconst\"" (space before colon) and
    // may append explicit defaults like resolvable : true
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
  }

  @Test
  void entitiesResolveInOneBatchedFind() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt0944947"),
            Map.of("__typename", "Title", "tconst", "tt0903747"),
            Map.of("__typename", "Title", "tconst", "tt0141842"))));
    assertThat(entities).extracting(e -> e.get("primaryTitle"))
        .containsExactly("Game of Thrones", "Breaking Bad", "The Sopranos");
    assertThat(counter.count("find"))
        .as("N representations must collapse into a single $in query via the DataLoader")
        .isEqualTo(1);
  }

  @Test
  void unknownRepresentationResolvesToNullWithoutError() {
    List<Object> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt9999999"))));
    assertThat(entities).hasSize(1);
    assertThat(entities.get(0)).isNull();
  }
}

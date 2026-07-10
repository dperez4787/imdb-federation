package org.perez_f_daniel.imdb.crew;

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
class CrewFederationIT extends AbstractMongoIntegrationTest {

  private static final String ENTITIES_QUERY = """
      query ($reps: [_Any!]!) {
        _entities(representations: $reps) {
          ... on Title { tconst directors { nconst } writers { nconst } }
        }
      }""";

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_crew");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt0944947\",\"directors\":\"nm0000001,nm0000002\",\"writers\":\"nm0000003\"}"),
        "title_crew");
    mongo.insert(Document.parse("{\"tconst\":\"tt0000001\",\"directors\":\"nm0005690\"}"),
        "title_crew");
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
  }

  @Test
  void directorsAndWritersExpandToNameStubsInOneFind() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt0944947"),
            Map.of("__typename", "Title", "tconst", "tt0000001"))));
    assertThat((List<Map<String, Object>>) entities.get(0).get("directors"))
        .extracting(n -> n.get("nconst")).containsExactly("nm0000001", "nm0000002");
    assertThat((List<Map<String, Object>>) entities.get(0).get("writers"))
        .extracting(n -> n.get("nconst")).containsExactly("nm0000003");
    assertThat(entities.get(1).get("writers")).isNull();
    assertThat(counter.count("find"))
        .as("directors + writers on 2 titles must share one batched find")
        .isEqualTo(1);
  }

  @Test
  void titleWithoutCrewRowHasNullFields() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt9999999"))));
    assertThat(entities.get(0).get("directors")).isNull();
    assertThat(entities.get(0).get("writers")).isNull();
  }
}

package org.perez_f_daniel.imdb.akas;

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
class AkasFederationIT extends AbstractMongoIntegrationTest {

  private static final String ENTITIES_QUERY = """
      query ($reps: [_Any!]!) {
        _entities(representations: $reps) {
          ... on Title {
            tconst akas { ordering title region types attributes isOriginalTitle }
          }
        }
      }""";

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_akas");
    // inserted out of order to prove the sort
    mongo.insert(Document.parse("""
        {"titleId":"tt0000001","ordering":2,"title":"Carmencita","region":"DE",
         "attributes":"literal title","isOriginalTitle":0}"""), "title_akas");
    mongo.insert(Document.parse("""
        {"titleId":"tt0000001","ordering":1,"title":"Carmencita","types":"original",
         "isOriginalTitle":1}"""), "title_akas");
    mongo.insert(Document.parse("""
        {"titleId":"tt0944947","ordering":1,"title":"Game of Thrones","types":"original",
         "isOriginalTitle":1}"""), "title_akas");
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
  }

  @Test
  void akasSortedByOrderingAndBatchedToOneFind() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt0000001"),
            Map.of("__typename", "Title", "tconst", "tt0944947"))));
    List<Map<String, Object>> akas = (List<Map<String, Object>>) entities.get(0).get("akas");
    assertThat(akas).extracting(a -> a.get("ordering")).containsExactly(1, 2);
    assertThat(akas.get(0)).containsEntry("isOriginalTitle", true)
        .containsEntry("types", List.of("original"));
    assertThat(akas.get(1)).containsEntry("region", "DE")
        .containsEntry("attributes", List.of("literal title"))
        .containsEntry("isOriginalTitle", false);
    assertThat(counter.count("find")).isEqualTo(1);
  }

  @Test
  void titleWithoutAkasGetsEmptyList() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath(
        ENTITIES_QUERY, "data._entities",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt9999999"))));
    assertThat(entities.get(0).get("akas")).isEqualTo(List.of());
  }
}

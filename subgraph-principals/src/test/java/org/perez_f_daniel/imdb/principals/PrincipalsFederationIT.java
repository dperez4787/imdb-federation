package org.perez_f_daniel.imdb.principals;

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
class PrincipalsFederationIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_principals");
    mongo.insert(Document.parse("""
        {"tconst":"tt0000001","ordering":2,"nconst":"nm0005690","category":"director"}"""),
        "title_principals");
    mongo.insert(Document.parse("""
        {"tconst":"tt0000001","ordering":1,"nconst":"nm1588970","category":"self",
         "characters":"[\\"Self\\"]"}"""), "title_principals");
    mongo.insert(Document.parse("""
        {"tconst":"tt0000002","ordering":1,"nconst":"nm1588970","category":"self",
         "characters":"not json"}"""), "title_principals");
    counter.reset();
  }

  @Test
  void serviceSdlExposesBothEntityExtensions() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Title @key\\(fields\\s*:\\s*\"tconst\"");
    assertThat(sdl).containsPattern("type Name @key\\(fields\\s*:\\s*\"nconst\"");
  }

  @Test
  void titlePrincipalsSortedWithParsedCharactersInOneFind() {
    List<Map<String, Object>> principals = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) {
            ... on Title { principals { ordering category characters name { nconst } } }
          }
        }""",
        "data._entities[0].principals",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt0000001"))));
    assertThat(principals).extracting(p -> p.get("ordering")).containsExactly(1, 2);
    assertThat(principals.get(0)).containsEntry("characters", List.of("Self"));
    assertThat((Map<String, Object>) principals.get(0).get("name"))
        .containsEntry("nconst", "nm1588970");
    assertThat(counter.count("find")).isEqualTo(1);
  }

  @Test
  void nameCreditsPagedSortedAcrossTitlesWithMalformedCharactersNull() {
    List<Map<String, Object>> credits = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) {
            ... on Name { credits(limit: 10) { ordering characters title { tconst } } }
          }
        }""",
        "data._entities[0].credits",
        Map.of("reps", List.of(Map.of("__typename", "Name", "nconst", "nm1588970"))));
    assertThat(credits).hasSize(2);
    assertThat((Map<String, Object>) credits.get(0).get("title"))
        .containsEntry("tconst", "tt0000001");
    assertThat(credits.get(1).get("characters")).isNull();
    assertThat(counter.count("aggregate")).isEqualTo(1);
  }

  @Test
  void principalsByNameRootQueryPaginates() {
    List<Map<String, Object>> credits = dgs.executeAndExtractJsonPath(
        "{ principalsByName(nconst: \"nm1588970\", limit: 1, offset: 1) { title { tconst } } }",
        "data.principalsByName");
    assertThat(credits).hasSize(1);
    assertThat((Map<String, Object>) credits.get(0).get("title"))
        .containsEntry("tconst", "tt0000002");
  }

  @Test
  void unknownKeysGetEmptyLists() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) {
            ... on Title { principals { ordering } }
            ... on Name { credits { ordering } }
          }
        }""",
        "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Title", "tconst", "tt9999999"),
            Map.of("__typename", "Name", "nconst", "nm9999999"))));
    assertThat(entities.get(0).get("principals")).isEqualTo(List.of());
    assertThat(entities.get(1).get("credits")).isEqualTo(List.of());
  }
}

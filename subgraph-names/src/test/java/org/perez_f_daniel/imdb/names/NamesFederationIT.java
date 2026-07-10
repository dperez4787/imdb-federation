package org.perez_f_daniel.imdb.names;

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
class NamesFederationIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("name_basics");
    mongo.insert(Document.parse("""
        {"nconst":"nm0000001","primaryName":"Fred Astaire","birthYear":1899,"deathYear":1987,
         "primaryProfession":"actor,miscellaneous,producer",
         "knownForTitles":"tt0072308,tt0050419"}"""), "name_basics");
    mongo.insert(Document.parse(
        "{\"nconst\":\"nm0000002\",\"primaryName\":\"Lauren Bacall\",\"birthYear\":1924}"),
        "name_basics");
    counter.reset();
  }

  @Test
  void serviceSdlExposesFederatedSchema() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern("type Name @key\\(fields\\s*:\\s*\"nconst\"");
  }

  @Test
  void nameMapsFieldsAndExpandsKnownForToStubs() {
    Map<String, Object> name = dgs.executeAndExtractJsonPath("""
        { name(nconst: "nm0000001") {
            primaryName birthYear deathYear primaryProfessions knownForTitles { tconst } } }""",
        "data.name");
    assertThat(name)
        .containsEntry("primaryName", "Fred Astaire")
        .containsEntry("primaryProfessions", List.of("actor", "miscellaneous", "producer"));
    assertThat((List<Map<String, Object>>) name.get("knownForTitles"))
        .extracting(t -> t.get("tconst"))
        .containsExactly("tt0072308", "tt0050419");
  }

  @Test
  void absentFieldsAreNull() {
    Map<String, Object> name = dgs.executeAndExtractJsonPath(
        "{ name(nconst: \"nm0000002\") { primaryName deathYear primaryProfessions knownForTitles { tconst } } }",
        "data.name");
    assertThat(name.get("deathYear")).isNull();
    assertThat(name.get("primaryProfessions")).isNull();
    assertThat(name.get("knownForTitles")).isNull();
  }

  @Test
  void entitiesResolveInOneBatchedFind() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) { ... on Name { nconst primaryName } }
        }""",
        "data._entities",
        Map.of("reps", List.of(
            Map.of("__typename", "Name", "nconst", "nm0000001"),
            Map.of("__typename", "Name", "nconst", "nm0000002"))));
    assertThat(entities).extracting(e -> e.get("primaryName"))
        .containsExactly("Fred Astaire", "Lauren Bacall");
    assertThat(counter.count("find")).isEqualTo(1);
  }
}

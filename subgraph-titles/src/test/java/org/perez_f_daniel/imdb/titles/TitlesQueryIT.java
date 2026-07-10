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

@SpringBootTest
@Import(MongoCounterTestConfig.class)
class TitlesQueryIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired MongoCommandCounter counter;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_basics");
    mongo.insert(Document.parse("""
        {"tconst":"tt0944947","titleType":"tvSeries","primaryTitle":"Game of Thrones",
         "originalTitle":"Game of Thrones","isAdult":0,"startYear":2011,"endYear":2019,
         "runtimeMinutes":57,"genres":"Action,Adventure,Drama"}"""), "title_basics");
    mongo.insert(Document.parse("""
        {"tconst":"tt0000001","titleType":"short","primaryTitle":"Carmencita",
         "originalTitle":"Carmencita","isAdult":0,"startYear":1894,"runtimeMinutes":1}"""),
        "title_basics");
    counter.reset();
  }

  @Test
  void titleReturnsMappedFields() {
    Map<String, Object> title = dgs.executeAndExtractJsonPath("""
        { title(tconst: "tt0944947") {
            tconst titleType primaryTitle isAdult startYear endYear runtimeMinutes genres } }""",
        "data.title");
    assertThat(title)
        .containsEntry("primaryTitle", "Game of Thrones")
        .containsEntry("titleType", "tvSeries")
        .containsEntry("isAdult", false)
        .containsEntry("startYear", 2011)
        .containsEntry("endYear", 2019)
        .containsEntry("genres", List.of("Action", "Adventure", "Drama"));
  }

  @Test
  void absentFieldsAreNull() {
    Map<String, Object> title = dgs.executeAndExtractJsonPath(
        "{ title(tconst: \"tt0000001\") { primaryTitle endYear genres } }", "data.title");
    assertThat(title).containsEntry("primaryTitle", "Carmencita");
    assertThat(title.get("endYear")).isNull();
    assertThat(title.get("genres")).isNull();
  }

  @Test
  void imgUrlIsNullWithoutOmdbKey() {
    // this context has no omdb.api-key -> the field degrades to null, never errors
    Object url = dgs.executeAndExtractJsonPath(
        "{ title(tconst: \"tt0944947\") { imgUrl } }", "data.title.imgUrl");
    assertThat(url).isNull();
  }

  @Test
  void unknownTitleIsNull() {
    Object title = dgs.executeAndExtractJsonPath(
        "{ title(tconst: \"tt9999999\") { tconst } }", "data.title");
    assertThat(title).isNull();
  }

  @Test
  void titlesPreservesInputOrderWithNullsAndBatchesToOneFind() {
    List<Map<String, Object>> titles = dgs.executeAndExtractJsonPath("""
        { titles(tconsts: ["tt0000001", "tt9999999", "tt0944947"]) { tconst } }""",
        "data.titles");
    assertThat(titles).hasSize(3);
    assertThat(titles.get(0)).containsEntry("tconst", "tt0000001");
    assertThat(titles.get(1)).isNull();
    assertThat(titles.get(2)).containsEntry("tconst", "tt0944947");
    assertThat(counter.count("find")).isEqualTo(1);
  }
}

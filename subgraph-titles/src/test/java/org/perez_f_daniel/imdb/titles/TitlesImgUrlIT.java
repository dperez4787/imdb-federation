package org.perez_f_daniel.imdb.titles;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/** imgUrl with a (fake) OMDb key configured — never the real key in tests. */
@SpringBootTest(properties = "omdb.api-key=test-omdb-key")
class TitlesImgUrlIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;

  @BeforeEach
  void seed() {
    mongo.dropCollection("title_basics");
    mongo.insert(Document.parse(
        "{\"tconst\":\"tt0944947\",\"titleType\":\"tvSeries\",\"primaryTitle\":\"Game of Thrones\"}"),
        "title_basics");
  }

  @Test
  void sdlExposesImgUrl() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).contains("imgUrl");
  }

  @Test
  void rootQueryReturnsKeyedOmdbUrl() {
    String url = dgs.executeAndExtractJsonPath(
        "{ title(tconst: \"tt0944947\") { imgUrl } }", "data.title.imgUrl");
    assertThat(url).isEqualTo("https://img.omdbapi.com/?i=tt0944947&apikey=test-omdb-key");
  }

  @Test
  void entityHydrationPathReturnsImgUrl() {
    List<Map<String, Object>> entities = dgs.executeAndExtractJsonPath("""
        query ($reps: [_Any!]!) {
          _entities(representations: $reps) { ... on Title { imgUrl } }
        }""",
        "data._entities",
        Map.of("reps", List.of(Map.of("__typename", "Title", "tconst", "tt0944947"))));
    assertThat(entities.get(0))
        .containsEntry("imgUrl", "https://img.omdbapi.com/?i=tt0944947&apikey=test-omdb-key");
  }
}

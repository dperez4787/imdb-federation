package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc
class RebuildIT extends AbstractMongoIntegrationTest {

  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;
  @Autowired MockMvc mvc;

  @BeforeEach
  void seed() {
    OrchestratorFixtures.seed(mongo);
  }

  @Test
  void fullRebuildProducesDerivedCollections() {
    rebuild.run(List.of(Step.values()));

    Document got = mongo.findById(OrchestratorFixtures.GOT, Document.class, "search_titles");
    assertThat(got.getString("titleType")).isEqualTo("tvSeries");
    assertThat(got.getString("primaryTitleLower")).isEqualTo("game of thrones");
    assertThat(got.getList("genres", String.class))
        .containsExactly("Action", "Adventure", "Drama");
    assertThat(got.getBoolean("isAdult")).isFalse();
    assertThat(got.getDouble("averageRating")).isEqualTo(9.2);
    assertThat(got.get("numVotes", Number.class).longValue()).isEqualTo(2000000L);

    Document adult = mongo.findById(OrchestratorFixtures.ADULT, Document.class, "search_titles");
    assertThat(adult.getBoolean("isAdult")).isTrue();

    // absent-stays-absent: unrated short has no rating fields, no endYear
    Document carmencita = mongo.findById(OrchestratorFixtures.SHORT, Document.class, "search_titles");
    assertThat(carmencita).doesNotContainKeys("averageRating", "numVotes", "endYear");

    // popularity: sum of knownForTitles' numVotes
    Document cranston = mongo.findById("nm0000002", Document.class, "search_names");
    assertThat(cranston.get("popularity", Number.class).longValue()).isEqualTo(3900000L);
    assertThat(cranston.getList("professions", String.class))
        .containsExactly("actor", "producer");
    Document clarke = mongo.findById("nm0000001", Document.class, "search_names");
    assertThat(clarke.get("popularity", Number.class).longValue()).isEqualTo(2000000L);
    Document noVotes = mongo.findById("nm0000004", Document.class, "search_names");
    assertThat(noVotes).doesNotContainKeys("popularity", "birthYear", "professions");

    // index contract
    assertThat(indexNames("search_titles")).contains("type_votes_id", "type_rating_id",
        "genres_type_votes_id", "votes_id", "rating_id", "year_id", "title_prefix", "title_text");
    assertThat(indexNames("search_names")).contains("professions_popularity_id", "popularity_id",
        "name_prefix_id", "name_text", "born_id");

    // meta + no leftovers
    Document meta = mongo.findById("rebuild", Document.class, "search_meta");
    assertThat(meta.getString("status")).isEqualTo("IDLE");
    assertThat(meta.getInteger("titleCount")).isEqualTo(5);
    assertThat(meta.getInteger("nameCount")).isEqualTo(4);
    assertThat(meta.getList("titleTypes", String.class))
        .containsExactlyInAnyOrder("movie", "short", "tvSeries");
    assertThat(meta.get("rebuiltAt")).isNotNull();
    assertThat(mongo.getCollectionNames())
        .doesNotContain("search_titles_next", "search_names_next", "tmp_kft");
  }

  @Test
  void rebuildIsIdempotent() {
    rebuild.run(List.of(Step.values()));
    rebuild.run(List.of(Step.values()));
    assertThat(mongo.estimatedCount("search_titles")).isEqualTo(5);
    assertThat(mongo.findById("rebuild", Document.class, "search_meta").getString("status"))
        .isEqualTo("IDLE");
  }

  @Test
  void concurrentRebuildIsRejected() {
    mongo.getCollection("search_meta").insertOne(new Document("_id", "rebuild")
        .append("status", "RUNNING").append("startedAt", Instant.now()));
    assertThatThrownBy(() -> rebuild.run(List.of(Step.values())))
        .isInstanceOf(RebuildService.RebuildLockedException.class);
  }

  @Test
  void runSessionBlocksOtherDriversBetweenSteps() {
    // driver A opens a session with a non-terminal step
    Map<String, Object> first = rebuild.run(List.of(Step.TITLES), "run-A");
    assertThat(first).containsEntry("sessionOpen", true);
    assertThat(mongo.findById("rebuild", Document.class, "search_meta"))
        .containsEntry("status", "IDLE").containsEntry("runId", "run-A");

    // driver B (different runId) and a one-shot (no runId) are both rejected
    assertThatThrownBy(() -> rebuild.run(List.of(Step.TITLES), "run-B"))
        .isInstanceOf(RebuildService.RebuildLockedException.class);
    assertThatThrownBy(() -> rebuild.run(List.of(Step.RATINGS)))
        .isInstanceOf(RebuildService.RebuildLockedException.class);

    // driver A continues, and the terminal FACETS step closes the session
    rebuild.run(List.of(Step.RATINGS, Step.NAMES, Step.KFT, Step.POPULARITY,
        Step.INDEXES, Step.PROMOTE), "run-A");
    Map<String, Object> last = rebuild.run(List.of(Step.FACETS), "run-A");
    assertThat(last).containsEntry("sessionOpen", false);
    assertThat(mongo.findById("rebuild", Document.class, "search_meta").get("runId")).isNull();

    // session closed: a different driver can acquire again
    rebuild.run(List.of(Step.TITLES), "run-B");
  }

  @Test
  void staleSessionIsClaimableByANewRun() {
    // a session abandoned 3h ago (driver died mid-sequence) must not block forever
    mongo.getCollection("search_meta").insertOne(new Document("_id", "rebuild")
        .append("status", "IDLE").append("runId", "run-A")
        .append("lastActivityAt",
            java.util.Date.from(Instant.now().minus(java.time.Duration.ofHours(3)))));
    Map<String, Object> r = rebuild.run(List.of(Step.TITLES), "run-C");
    assertThat(r).containsEntry("status", "OK");
    assertThat(mongo.findById("rebuild", Document.class, "search_meta"))
        .containsEntry("runId", "run-C");
  }

  @Test
  void restEndpointRunsAndReportsStatus() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/admin/rebuild"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("OK"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.promote").exists());
    mvc.perform(MockMvcRequestBuilders.get("/admin/rebuild/status"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("IDLE"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.titleCount").value(5));
  }

  @Test
  void conflictWhileRunning() throws Exception {
    mongo.getCollection("search_meta").insertOne(new Document("_id", "rebuild")
        .append("status", "RUNNING").append("startedAt", Instant.now()));
    mvc.perform(MockMvcRequestBuilders.post("/admin/rebuild"))
        .andExpect(MockMvcResultMatchers.status().isConflict());
  }

  private List<String> indexNames(String collection) {
    List<String> names = new ArrayList<>();
    mongo.getCollection(collection).listIndexes()
        .forEach(d -> names.add(d.getString("name")));
    return names;
  }
}

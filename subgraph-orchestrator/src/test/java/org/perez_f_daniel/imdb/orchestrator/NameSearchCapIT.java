package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.testsupport.AbstractMongoIntegrationTest;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Candidate-cap semantics with a cap of 1: only the most popular matching title survives. */
@SpringBootTest(properties = "imdb.search.title-candidate-cap=1")
class NameSearchCapIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    rebuild.run(List.of(Step.values()));
  }

  @Test
  void openEndedScopeTruncatesToMostPopularTitleDeterministically() {
    // Drama matches GoT (2.0M), BB (1.9M); cap=1 keeps GoT only -> its two principals
    Map<String, Object> result = dgs.executeAndExtractJsonPath("""
        { searchNames(filter: {inGenres: ["Drama"]})
          { titleCandidatesCapped items { nconst } } }""",
        "data.searchNames");
    assertThat(result).containsEntry("titleCandidatesCapped", true);
    assertThat((List<Map<String, Object>>) result.get("items"))
        .extracting(i -> i.get("nconst"))
        .containsExactly("nm0000001", "nm0000002");
  }
}

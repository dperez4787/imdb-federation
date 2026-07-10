package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
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

@SpringBootTest
class OrchestratorFederationIT extends AbstractMongoIntegrationTest {

  @Autowired DgsQueryExecutor dgs;
  @Autowired MongoTemplate mongo;
  @Autowired RebuildService rebuild;

  @BeforeEach
  void seedAndRebuild() {
    OrchestratorFixtures.seed(mongo);
    rebuild.run(List.of(Step.values()));
  }

  @Test
  void sdlDeclaresNonResolvableStubsForBothEntities() {
    String sdl = dgs.executeAndExtractJsonPath("{ _service { sdl } }", "data._service.sdl");
    assertThat(sdl).containsPattern(
        "type Title @key\\(fields\\s*:\\s*\"tconst\",\\s*resolvable\\s*:\\s*false\\)");
    assertThat(sdl).containsPattern(
        "type Name @key\\(fields\\s*:\\s*\"nconst\",\\s*resolvable\\s*:\\s*false\\)");
  }

  @Test
  void searchReturnsKeyStubsOnly() {
    List<Map<String, Object>> items = dgs.executeAndExtractJsonPath(
        "{ searchTitles(limit: 1) { items { tconst } } }", "data.searchTitles.items");
    assertThat(items).containsExactly(Map.of("tconst", OrchestratorFixtures.GOT));
  }

  @Test
  void searchInfoReportsFreshness() {
    Map<String, Object> info = dgs.executeAndExtractJsonPath(
        "{ searchInfo { rebuiltAt titleCount nameCount } }", "data.searchInfo");
    assertThat((String) info.get("rebuiltAt")).isNotNull();
    assertThat(info).containsEntry("titleCount", 5).containsEntry("nameCount", 4);
  }

  @Test
  void validationSurfacesAsGraphqlError() {
    ExecutionResult result = dgs.execute(
        "{ searchTitles(offset: 99999) { items { tconst } } }");
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0).getMessage()).contains("offset");
  }
}

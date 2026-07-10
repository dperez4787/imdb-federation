package org.perez_f_daniel.imdb.common.governance;

import java.util.List;
import java.util.function.Supplier;
import org.perez_f_daniel.imdb.common.governance.GovernedFields.Declaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.web.client.RestClient;

/**
 * Pushes this subgraph's @governed declarations to the policy service at
 * startup: the schema stays the source of truth for WHAT is governed, the
 * policy service for WHO may read it. The PUT is idempotent on the service
 * side (unchanged sets are no-ops; removed fields get orphaned, not deleted).
 *
 * Registration failure never fails boot — a subgraph must serve even when
 * the governance plane is down; enforcement lives at the router, which
 * fail-statically keeps its last bundle.
 */
public class GovernedFieldsRegistrar implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(GovernedFieldsRegistrar.class);
  private static final int ATTEMPTS = 3;

  private final GraphQlSource graphQlSource;
  private final RestClient restClient;
  private final String subgraph;
  private final Supplier<String> idTokenSupplier;

  public GovernedFieldsRegistrar(
      GraphQlSource graphQlSource,
      RestClient restClient,
      String subgraph,
      Supplier<String> idTokenSupplier) {
    this.graphQlSource = graphQlSource;
    this.restClient = restClient;
    this.subgraph = subgraph;
    this.idTokenSupplier = idTokenSupplier;
  }

  record RegistrationRequest(List<Declaration> fields) {}

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    List<Declaration> declarations = GovernedFields.extract(graphQlSource.schema());
    log.info("registering {} @governed declaration(s) for subgraph {}", declarations.size(), subgraph);

    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      try {
        var request = restClient.put().uri("/v1/registrations/{subgraph}", subgraph);
        String idToken = idTokenSupplier.get();
        if (idToken != null) {
          request = request.header("Authorization", "Bearer " + idToken);
        }
        String body =
            request
                .header("Content-Type", "application/json")
                .header("X-Registered-By", "subgraph:" + subgraph)
                .body(new RegistrationRequest(declarations))
                .retrieve()
                .body(String.class);
        log.info("governed-field registration applied: {}", body);
        return;
      } catch (Exception e) {
        log.warn("governed-field registration attempt {}/{} failed: {}", attempt, ATTEMPTS, e.getMessage());
        if (attempt < ATTEMPTS) {
          try {
            Thread.sleep(2000L * attempt);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    }
    log.error(
        "governed-field registration failed after {} attempts; the policy service keeps its last known "
            + "declaration set for {} (enforcement at the router is unaffected)",
        ATTEMPTS,
        subgraph);
  }
}

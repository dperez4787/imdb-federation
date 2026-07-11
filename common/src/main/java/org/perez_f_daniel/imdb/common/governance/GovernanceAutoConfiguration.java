package org.perez_f_daniel.imdb.common.governance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.web.client.RestClient;

/**
 * Active only when POLICY_SERVICE_URL is set (deploy.yml sets it in
 * production; local runs and tests are unaffected). The subgraph name
 * defaults to spring.application.name minus its "subgraph-" prefix — the
 * same names compose.sh and the policy service use.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "policy.service.url")
public class GovernanceAutoConfiguration {

  @Bean
  @ConditionalOnBean(GraphQlSource.class)
  public GovernedFieldsRegistrar governedFieldsRegistrar(
      GraphQlSource graphQlSource,
      @Value("${policy.service.url}") String policyServiceUrl,
      @Value("${policy.registration.subgraph:}") String subgraphOverride,
      @Value("${spring.application.name:unknown}") String applicationName) {
    String subgraph =
        !subgraphOverride.isBlank()
            ? subgraphOverride
            : applicationName.replaceFirst("^subgraph-", "");
    return new GovernedFieldsRegistrar(
        graphQlSource,
        RestClient.builder().baseUrl(policyServiceUrl).build(),
        subgraph,
        GoogleIdTokens.supplier(policyServiceUrl));
  }
}

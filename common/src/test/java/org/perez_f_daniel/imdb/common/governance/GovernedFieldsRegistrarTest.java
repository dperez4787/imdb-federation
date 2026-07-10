package org.perez_f_daniel.imdb.common.governance;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GovernedFieldsRegistrarTest {

  @Test
  void putsDeclarationsWithIdTokenWhenAvailable() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://policy.example");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://policy.example/v1/registrations/ratings"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer fake-id-token"))
        .andExpect(header("X-Registered-By", "subgraph:ratings"))
        .andExpect(content().json(
            """
            {"fields":[{"coordinate":"Rating.numVotes","reason":"vote counts are analyst-only"}]}
            """))
        .andRespond(withSuccess(
            "{\"subgraph\":\"ratings\",\"added\":0,\"updated\":0,\"orphaned\":0,\"changed\":false,\"revision\":1}",
            MediaType.APPLICATION_JSON));

    GraphQlSource source =
        GraphQlSource.builder(
                UnExecutableSchemaGenerator.makeUnExecutableSchema(
                    new SchemaParser()
                        .parse(
                            """
                            directive @governed(reason: String) on FIELD_DEFINITION
                            type Query { rating: Rating }
                            type Rating {
                              averageRating: Float!
                              numVotes: Int! @governed(reason: "vote counts are analyst-only")
                            }
                            """)))
            .build();

    new GovernedFieldsRegistrar(source, builder.build(), "ratings", () -> "fake-id-token")
        .onApplicationEvent(null);

    server.verify();
  }
}

package org.perez_f_daniel.imdb.common.governance;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.governance.GovernedFields.Declaration;

class GovernedFieldsTest {

  private static GraphQLSchema schema(String sdl) {
    return UnExecutableSchemaGenerator.makeUnExecutableSchema(new SchemaParser().parse(sdl));
  }

  @Test
  void extractsCoordinatesAndReasons() {
    List<Declaration> declarations =
        GovernedFields.extract(
            schema(
                """
                directive @governed(reason: String) on FIELD_DEFINITION
                type Query { rating: Rating }
                type Rating {
                  averageRating: Float!
                  numVotes: Int! @governed(reason: "vote counts are analyst-only")
                }
                """));

    assertThat(declarations)
        .containsExactly(new Declaration("Rating.numVotes", "vote counts are analyst-only"));
  }

  @Test
  void reasonIsOptionalAndUnannotatedSchemasAreEmpty() {
    assertThat(
            GovernedFields.extract(
                schema(
                    """
                    directive @governed(reason: String) on FIELD_DEFINITION
                    type Query { secret: String @governed }
                    """)))
        .containsExactly(new Declaration("Query.secret", null));

    assertThat(GovernedFields.extract(schema("type Query { open: String }"))).isEmpty();
  }
}

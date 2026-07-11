package org.perez_f_daniel.imdb.common.governance;

import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import java.util.ArrayList;
import java.util.List;

/** Extracts @governed declarations from a schema as Type.field coordinates. */
public final class GovernedFields {

  public static final String DIRECTIVE = "governed";

  public record Declaration(String coordinate, String reason) {}

  private GovernedFields() {}

  public static List<Declaration> extract(GraphQLSchema schema) {
    List<Declaration> declarations = new ArrayList<>();
    for (var type : schema.getAllTypesAsList()) {
      if (!(type instanceof GraphQLObjectType objectType) || type.getName().startsWith("__")) {
        continue;
      }
      for (GraphQLFieldDefinition field : objectType.getFieldDefinitions()) {
        GraphQLAppliedDirective directive = field.getAppliedDirective(DIRECTIVE);
        if (directive == null) {
          continue;
        }
        String reason = null;
        GraphQLAppliedDirectiveArgument argument = directive.getArgument("reason");
        if (argument != null && argument.getValue() instanceof String s) {
          reason = s;
        }
        declarations.add(new Declaration(objectType.getName() + "." + field.getName(), reason));
      }
    }
    return declarations;
  }
}

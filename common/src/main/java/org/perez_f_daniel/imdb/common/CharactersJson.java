package org.perez_f_daniel.imdb.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * title_principals.characters holds a JSON-array-like string, e.g. {@code ["Self"]}.
 * Malformed rows exist in the source data; they map to null rather than a field error.
 */
public final class CharactersJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private CharactersJson() {}

  public static List<String> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      List<String> values = MAPPER.readValue(raw, STRING_LIST);
      return values.isEmpty() ? null : values;
    } catch (Exception e) {
      return null;
    }
  }
}

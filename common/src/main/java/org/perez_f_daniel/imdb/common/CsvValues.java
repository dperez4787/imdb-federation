package org.perez_f_daniel.imdb.common;

import java.util.Arrays;
import java.util.List;

/**
 * IMDb multi-value columns are stored as comma-separated strings (never arrays)
 * by the import pipeline; fields sourced from \N are absent entirely.
 */
public final class CsvValues {

  private CsvValues() {}

  /** Splits a csv field into a list; returns null for absent/blank input or no usable values. */
  public static List<String> split(String csv) {
    if (csv == null || csv.isBlank()) {
      return null;
    }
    List<String> values = Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
    return values.isEmpty() ? null : values;
  }
}

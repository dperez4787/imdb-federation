package org.perez_f_daniel.imdb.orchestrator.api;

import java.util.List;

public record NameSearchFilter(
    String query,
    String namePrefix,
    List<String> professions,
    Integer bornFrom,
    Integer bornTo,
    List<String> inTitles,
    List<String> inGenres,
    Integer activeFrom,
    Integer activeTo,
    List<String> categories) {

  public boolean hasInTitles() {
    return inTitles != null && !inTitles.isEmpty();
  }

  /** Any dimension that scopes the search to titles (drives the join strategies). */
  public boolean titleScoped() {
    return hasInTitles()
        || (inGenres != null && !inGenres.isEmpty())
        || activeFrom != null
        || activeTo != null;
  }

  public boolean hasText() {
    return query != null && !query.isBlank();
  }

  public static NameSearchFilter empty() {
    return new NameSearchFilter(null, null, null, null, null, null, null, null, null, null);
  }
}

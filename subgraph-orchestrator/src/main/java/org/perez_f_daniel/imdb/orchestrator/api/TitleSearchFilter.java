package org.perez_f_daniel.imdb.orchestrator.api;

import java.util.List;

public record TitleSearchFilter(
    String query,
    String titlePrefix,
    List<String> titleTypes,
    List<String> genresAny,
    List<String> genresAll,
    Integer startYearFrom,
    Integer startYearTo,
    Integer runtimeFrom,
    Integer runtimeTo,
    Double ratingFrom,
    Double ratingTo,
    Integer votesFrom,
    Boolean includeAdult,
    List<String> withPeople,
    PeopleMatchMode peopleMode,
    List<String> peopleCategories) {

  public boolean adultIncluded() {
    return Boolean.TRUE.equals(includeAdult);
  }

  public PeopleMatchMode peopleModeOrDefault() {
    return peopleMode == null ? PeopleMatchMode.ALL : peopleMode;
  }

  public boolean hasPeople() {
    return withPeople != null && !withPeople.isEmpty();
  }

  public boolean hasText() {
    return query != null && !query.isBlank();
  }

  public static TitleSearchFilter empty() {
    return new TitleSearchFilter(
        null, null, null, null, null, null, null, null, null, null, null, null,
        false, null, PeopleMatchMode.ALL, null);
  }
}

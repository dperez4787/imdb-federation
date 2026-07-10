package org.perez_f_daniel.imdb.orchestrator.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Caps and defaults for search execution; overridable via imdb.search.* properties. */
@ConfigurationProperties("imdb.search")
public record SearchProperties(
    Integer titleCandidateCap,
    Integer textCandidateCap,
    Integer countCap,
    Integer maxLimit,
    Integer defaultLimit,
    Integer maxOffset,
    Integer withPeopleMax,
    Integer inTitlesMax) {

  public SearchProperties {
    titleCandidateCap = titleCandidateCap == null ? 5000 : titleCandidateCap;
    textCandidateCap = textCandidateCap == null ? 20000 : textCandidateCap;
    countCap = countCap == null ? 10000 : countCap;
    maxLimit = maxLimit == null ? 100 : maxLimit;
    defaultLimit = defaultLimit == null ? 25 : defaultLimit;
    maxOffset = maxOffset == null ? 10000 : maxOffset;
    withPeopleMax = withPeopleMax == null ? 20 : withPeopleMax;
    inTitlesMax = inTitlesMax == null ? 100 : inTitlesMax;
  }
}

package org.perez_f_daniel.imdb.titles;

import org.springframework.data.mongodb.core.mapping.Document;

/** Raw title_basics document; \N-sourced fields are absent and map to null. */
@Document("title_basics")
public record TitleDoc(
    String tconst,
    String titleType,
    String primaryTitle,
    String originalTitle,
    Integer isAdult,
    Integer startYear,
    Integer endYear,
    Integer runtimeMinutes,
    String genres) {}

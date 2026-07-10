package org.perez_f_daniel.imdb.titles;

import java.util.List;

public record Title(
    String tconst,
    String titleType,
    String primaryTitle,
    String originalTitle,
    Boolean isAdult,
    Integer startYear,
    Integer endYear,
    Integer runtimeMinutes,
    List<String> genres) {}

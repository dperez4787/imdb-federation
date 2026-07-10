package org.perez_f_daniel.imdb.names;

import java.util.List;

/** knownForTitlesCsv is not a schema field; the knownForTitles resolver expands it to Title stubs. */
public record Name(
    String nconst,
    String primaryName,
    Integer birthYear,
    Integer deathYear,
    List<String> primaryProfessions,
    String knownForTitlesCsv) {}

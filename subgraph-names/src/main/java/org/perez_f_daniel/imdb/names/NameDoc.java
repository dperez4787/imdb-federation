package org.perez_f_daniel.imdb.names;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("name_basics")
public record NameDoc(
    String nconst,
    String primaryName,
    Integer birthYear,
    Integer deathYear,
    String primaryProfession,
    String knownForTitles) {}

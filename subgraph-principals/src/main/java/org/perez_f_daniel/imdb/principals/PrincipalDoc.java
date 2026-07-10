package org.perez_f_daniel.imdb.principals;

import org.springframework.data.mongodb.core.mapping.Document;

/** characters holds a JSON-array-like string, e.g. ["Self"]; job/characters often absent. */
@Document("title_principals")
public record PrincipalDoc(
    String tconst,
    Integer ordering,
    String nconst,
    String category,
    String job,
    String characters) {}

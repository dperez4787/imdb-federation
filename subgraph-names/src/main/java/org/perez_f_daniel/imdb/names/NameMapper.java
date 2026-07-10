package org.perez_f_daniel.imdb.names;

import org.perez_f_daniel.imdb.common.CsvValues;

public final class NameMapper {

  private NameMapper() {}

  public static Name toGraphql(NameDoc doc) {
    return new Name(
        doc.nconst(),
        doc.primaryName(),
        doc.birthYear(),
        doc.deathYear(),
        CsvValues.split(doc.primaryProfession()),
        doc.knownForTitles());
  }
}

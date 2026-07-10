package org.perez_f_daniel.imdb.titles;

import org.perez_f_daniel.imdb.common.CsvValues;
import org.perez_f_daniel.imdb.common.Fields;

public final class TitleMapper {

  private TitleMapper() {}

  public static Title toGraphql(TitleDoc doc) {
    return new Title(
        doc.tconst(),
        doc.titleType(),
        doc.primaryTitle(),
        doc.originalTitle(),
        Fields.toBoolean(doc.isAdult()),
        doc.startYear(),
        doc.endYear(),
        doc.runtimeMinutes(),
        CsvValues.split(doc.genres()));
  }
}

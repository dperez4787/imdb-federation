package org.perez_f_daniel.imdb.principals;

import org.perez_f_daniel.imdb.common.CharactersJson;

public final class PrincipalMapper {

  private PrincipalMapper() {}

  public static Principal toGraphql(PrincipalDoc doc) {
    return new Principal(
        doc.ordering(),
        doc.category(),
        doc.job(),
        CharactersJson.parse(doc.characters()),
        new Name(doc.nconst()),
        new Title(doc.tconst()));
  }
}

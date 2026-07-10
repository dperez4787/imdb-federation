package org.perez_f_daniel.imdb.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharactersJsonTest {

  @Test
  void parsesJsonArrayString() {
    assertThat(CharactersJson.parse("[\"Self\"]")).containsExactly("Self");
    assertThat(CharactersJson.parse("[\"Batman\",\"Bruce Wayne\"]"))
        .containsExactly("Batman", "Bruce Wayne");
  }

  @Test
  void parsesEscapedQuotes() {
    assertThat(CharactersJson.parse("[\"D'Artagnan\",\"The \\\"Boss\\\"\"]"))
        .containsExactly("D'Artagnan", "The \"Boss\"");
  }

  @Test
  void malformedInputYieldsNull() {
    assertThat(CharactersJson.parse("not json")).isNull();
    assertThat(CharactersJson.parse("[unterminated")).isNull();
    assertThat(CharactersJson.parse("{\"a\":1}")).isNull();
  }

  @Test
  void absentBlankOrEmptyArrayYieldsNull() {
    assertThat(CharactersJson.parse(null)).isNull();
    assertThat(CharactersJson.parse("")).isNull();
    assertThat(CharactersJson.parse("[]")).isNull();
  }
}

package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.orchestrator.search.Regexes;

class RegexesTest {

  @Test
  void escapesRegexMetacharacters() {
    assertThat(Regexes.escape(".*")).isEqualTo("\\.\\*");
    assertThat(Regexes.escape("a$b^c(d)")).isEqualTo("a\\$b\\^c\\(d\\)");
    assertThat(Regexes.escape("\\Qevil\\E")).isEqualTo("\\\\Qevil\\\\E");
  }

  @Test
  void keepsAlphanumericsAndUnicodeLetters() {
    assertThat(Regexes.escape("abc123")).isEqualTo("abc123");
    assertThat(Regexes.escape("Amélie")).isEqualTo("Amélie");
  }

  @Test
  void prefixAnchorsAndLowercases() {
    assertThat(Regexes.prefix("  Brea ")).isEqualTo("^brea");
    assertThat(Regexes.prefix("A.I")).isEqualTo("^a\\.i");
  }

  @Test
  void substringLowercases() {
    assertThat(Regexes.substring("DiCaprio")).isEqualTo("dicaprio");
  }
}

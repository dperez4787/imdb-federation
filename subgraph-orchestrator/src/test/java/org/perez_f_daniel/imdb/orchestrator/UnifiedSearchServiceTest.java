package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.orchestrator.search.UnifiedSearchService;

/** Pure-builder coverage for the unified search tokenizer and match shape. */
class UnifiedSearchServiceTest {

  private static final int MIN_PREFIX = 3;

  @Test
  void tokenizeLowercasesAndSplitsOnNonAlphanumerics() {
    assertThat(UnifiedSearchService.tokenize("Kiss Me, Deadly!"))
        .containsExactly("kiss", "me", "deadly");
    assertThat(UnifiedSearchService.tokenize("Monsters, Inc."))
        .containsExactly("monsters", "inc");
    assertThat(UnifiedSearchService.tokenize("  Breaking   Bad  "))
        .containsExactly("breaking", "bad");
    assertThat(UnifiedSearchService.tokenize("2001: A Space Odyssey"))
        .containsExactly("2001", "a", "space", "odyssey");
  }

  @Test
  void tokenizeMirrorsMongoAsciiOnlyToLower() {
    // $toLower leaves non-ASCII untouched; the Java twin must not fold É -> é
    assertThat(UnifiedSearchService.tokenize("AMÉLIE")).containsExactly("amÉlie");
    assertThat(UnifiedSearchService.tokenize("amélie")).containsExactly("amélie");
  }

  @Test
  void tokenizeKeepsOrderAndDuplicates() {
    assertThat(UnifiedSearchService.tokenize("New York, New York"))
        .containsExactly("new", "york", "new", "york");
    assertThat(UnifiedSearchService.tokenize("!!!")).isEmpty();
  }

  @Test
  void singleTokenMatchesExactlyNeverAsPrefix() {
    assertThat(UnifiedSearchService.termsMatch("titleTerms", List.of("jennifer"), MIN_PREFIX))
        .isEqualTo(new Document("titleTerms", "jennifer"));
  }

  @Test
  void trailingTokenOfMultiTokenQueryPrefixMatches() {
    Document m = UnifiedSearchService.termsMatch(
        "nameTerms", List.of("jennifer", "anisto"), MIN_PREFIX);
    assertThat(m).isEqualTo(new Document("$and", List.of(
        new Document("nameTerms", "jennifer"),
        new Document("nameTerms", new Document("$regex", "^anisto")))));
  }

  @Test
  void shortTrailingTokenStaysExact() {
    Document m = UnifiedSearchService.termsMatch(
        "titleTerms", List.of("breaking", "ba"), MIN_PREFIX);
    assertThat(m).isEqualTo(new Document("$and", List.of(
        new Document("titleTerms", "breaking"),
        new Document("titleTerms", "ba"))));
  }

  @Test
  void duplicateExactTokensCollapse() {
    Document m = UnifiedSearchService.termsMatch(
        "titleTerms", List.of("new", "york", "new", "york"), MIN_PREFIX);
    assertThat(m).isEqualTo(new Document("$and", List.of(
        new Document("titleTerms", "new"),
        new Document("titleTerms", "york"),
        new Document("titleTerms", new Document("$regex", "^york")))));
  }

  @Test
  void regexMetacharactersInTrailingTokenAreEscapedByTokenizerContract() {
    // tokens can only be letters/digits, but termsMatch still escapes defensively
    Document m = UnifiedSearchService.termsMatch(
        "titleTerms", List.of("game", "thro"), MIN_PREFIX);
    assertThat(m.getList("$and", Document.class).get(1)
        .get("titleTerms", Document.class).getString("$regex")).isEqualTo("^thro");
  }
}

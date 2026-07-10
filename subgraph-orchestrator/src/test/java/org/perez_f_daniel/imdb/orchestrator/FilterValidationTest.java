package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.graphql.dgs.exceptions.DgsBadRequestException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.orchestrator.api.FilterValidation;
import org.perez_f_daniel.imdb.orchestrator.api.NameSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.NameSort;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSort;
import org.perez_f_daniel.imdb.orchestrator.search.SearchProperties;

class FilterValidationTest {

  private final FilterValidation validation = new FilterValidation(
      new SearchProperties(null, null, null, null, null, null, null, null));

  private static TitleSearchFilter titles(java.util.function.UnaryOperator<TitleFilterBuilder> fn) {
    return fn.apply(new TitleFilterBuilder()).build();
  }

  @Test
  void emptyFilterIsValid() {
    assertThatCode(() ->
        validation.validate(TitleSearchFilter.empty(), TitleSort.RELEVANCE, null, null))
        .doesNotThrowAnyException();
    assertThatCode(() ->
        validation.validate(NameSearchFilter.empty(), NameSort.RELEVANCE, null, null))
        .doesNotThrowAnyException();
  }

  @Test
  void queryAndPrefixAreExclusive() {
    TitleSearchFilter f = titles(b -> b.query("thrones").titlePrefix("ga"));
    assertThatThrownBy(() -> validation.validate(f, TitleSort.RELEVANCE, null, null))
        .isInstanceOf(DgsBadRequestException.class)
        .hasMessageContaining("mutually exclusive");
  }

  @Test
  void shortPrefixRejected() {
    TitleSearchFilter f = titles(b -> b.titlePrefix("g"));
    assertThatThrownBy(() -> validation.validate(f, TitleSort.RELEVANCE, null, null))
        .isInstanceOf(DgsBadRequestException.class);
  }

  @Test
  void listCapsEnforced() {
    TitleSearchFilter f = titles(b -> b.withPeople(Collections.nCopies(21, "nm1")));
    assertThatThrownBy(() -> validation.validate(f, TitleSort.RELEVANCE, null, null))
        .isInstanceOf(DgsBadRequestException.class)
        .hasMessageContaining("withPeople");
  }

  @Test
  void invertedRangesRejected() {
    TitleSearchFilter f = titles(b -> b.startYearFrom(2000).startYearTo(1990));
    assertThatThrownBy(() -> validation.validate(f, TitleSort.RELEVANCE, null, null))
        .isInstanceOf(DgsBadRequestException.class);
  }

  @Test
  void deepOffsetRejected() {
    assertThatThrownBy(() ->
        validation.validate(TitleSearchFilter.empty(), TitleSort.RELEVANCE, null, 10001))
        .isInstanceOf(DgsBadRequestException.class)
        .hasMessageContaining("offset");
  }

  @Test
  void creditsSortRequiresTitleScope() {
    assertThatThrownBy(() ->
        validation.validate(NameSearchFilter.empty(), NameSort.CREDITS_DESC, null, null))
        .isInstanceOf(DgsBadRequestException.class)
        .hasMessageContaining("CREDITS_DESC");
  }

  @Test
  void categoriesRequireTitleScope() {
    NameSearchFilter f = new NameSearchFilter(
        null, null, null, null, null, null, null, null, null, List.of("actor"));
    assertThatThrownBy(() -> validation.validate(f, NameSort.RELEVANCE, null, null))
        .isInstanceOf(DgsBadRequestException.class)
        .hasMessageContaining("categories");
    NameSearchFilter scoped = new NameSearchFilter(
        null, null, null, null, null, List.of("tt1"), null, null, null, List.of("actor"));
    assertThatCode(() -> validation.validate(scoped, NameSort.RELEVANCE, null, null))
        .doesNotThrowAnyException();
  }

  /** Minimal builder so tests stay readable despite the wide record. */
  private static final class TitleFilterBuilder {
    private String query;
    private String titlePrefix;
    private List<String> withPeople;
    private Integer startYearFrom;
    private Integer startYearTo;

    TitleFilterBuilder query(String v) { this.query = v; return this; }
    TitleFilterBuilder titlePrefix(String v) { this.titlePrefix = v; return this; }
    TitleFilterBuilder withPeople(List<String> v) { this.withPeople = v; return this; }
    TitleFilterBuilder startYearFrom(Integer v) { this.startYearFrom = v; return this; }
    TitleFilterBuilder startYearTo(Integer v) { this.startYearTo = v; return this; }

    TitleSearchFilter build() {
      return new TitleSearchFilter(query, titlePrefix, null, null, null, startYearFrom,
          startYearTo, null, null, null, null, null, false, withPeople, null, null);
    }
  }
}

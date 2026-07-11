package org.perez_f_daniel.imdb.orchestrator.api;

import com.netflix.graphql.dgs.exceptions.DgsBadRequestException;
import java.util.List;
import org.perez_f_daniel.imdb.orchestrator.search.SearchProperties;
import org.springframework.stereotype.Component;

/** All request validation; failures surface as BAD_REQUEST GraphQL errors. */
@Component
public class FilterValidation {

  private final SearchProperties props;

  public FilterValidation(SearchProperties props) {
    this.props = props;
  }

  public void validate(TitleSearchFilter f, TitleSort sort, Integer limit, Integer offset) {
    paging(offset);
    exclusive(f.query(), f.titlePrefix(), "query", "titlePrefix");
    prefixLength(f.titlePrefix(), "titlePrefix", props.minPrefixLength());
    cap(f.withPeople(), props.withPeopleMax(), "withPeople");
    cap(f.genresAny(), 10, "genresAny");
    cap(f.genresAll(), 5, "genresAll");
    range(f.startYearFrom(), f.startYearTo(), "startYear");
    range(f.runtimeFrom(), f.runtimeTo(), "runtime");
    range(f.ratingFrom(), f.ratingTo(), "rating");
  }

  public void validate(NameSearchFilter f, NameSort sort, Integer limit, Integer offset) {
    paging(offset);
    exclusive(f.query(), f.namePrefix(), "query", "namePrefix");
    prefixLength(f.namePrefix(), "namePrefix", props.minPrefixLength());
    cap(f.inTitles(), props.inTitlesMax(), "inTitles");
    cap(f.inGenres(), 10, "inGenres");
    range(f.bornFrom(), f.bornTo(), "born");
    range(f.activeFrom(), f.activeTo(), "active");
    if (sort == NameSort.CREDITS_DESC && !f.titleScoped()) {
      throw new DgsBadRequestException(
          "CREDITS_DESC requires a title-scoped filter (inTitles/inGenres/activeFrom/activeTo)");
    }
    if (f.categories() != null && !f.categories().isEmpty() && !f.titleScoped()) {
      throw new DgsBadRequestException(
          "categories requires a title-scoped filter (inTitles/inGenres/activeFrom/activeTo)");
    }
  }

  private void paging(Integer offset) {
    if (offset != null && offset > props.maxOffset()) {
      throw new DgsBadRequestException("offset must be <= " + props.maxOffset());
    }
  }

  private static void exclusive(String a, String b, String an, String bn) {
    if (a != null && !a.isBlank() && b != null && !b.isBlank()) {
      throw new DgsBadRequestException(an + " and " + bn + " are mutually exclusive");
    }
  }

  private static void prefixLength(String prefix, String field, int min) {
    if (prefix != null && prefix.strip().length() < min) {
      throw new DgsBadRequestException(field + " must be at least " + min + " characters");
    }
  }

  private static void cap(List<?> list, int max, String field) {
    if (list != null && list.size() > max) {
      throw new DgsBadRequestException(field + " accepts at most " + max + " values");
    }
  }

  private static <T extends Comparable<T>> void range(T from, T to, String field) {
    if (from != null && to != null && from.compareTo(to) > 0) {
      throw new DgsBadRequestException(field + "From must be <= " + field + "To");
    }
  }
}

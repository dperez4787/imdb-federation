package org.perez_f_daniel.imdb.orchestrator.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.bson.Document;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.PeopleMatchMode;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSort;

/**
 * Pure pipeline builders for title search — no Mongo dependency, unit-testable.
 * Invariants: every $sort ends with an _id tiebreak (deterministic paging); every
 * truncation is preceded by a deterministic sort.
 */
public final class TitlePipelines {

  public enum Strategy { PLAIN, PEOPLE_FIRST, TEXT_FIRST }

  private TitlePipelines() {}

  public static Strategy strategyFor(TitleSearchFilter f) {
    if (f.hasText() && f.hasPeople()) {
      return Strategy.TEXT_FIRST;
    }
    return f.hasPeople() ? Strategy.PEOPLE_FIRST : Strategy.PLAIN;
  }

  /**
   * Prefix-led queries must be pinned to the prefix index: left to itself the
   * planner may walk a popularity index testing the regex as a residual over
   * millions of entries (observed: 7s for a common prefix).
   */
  public static java.util.Optional<String> hintFor(TitleSearchFilter f) {
    return strategyFor(f) == Strategy.PLAIN && hasPrefix(f) && !f.hasText()
        ? java.util.Optional.of("title_prefix")
        : java.util.Optional.empty();
  }

  private static boolean hasPrefix(TitleSearchFilter f) {
    return f.titlePrefix() != null && !f.titlePrefix().isBlank();
  }

  /** Collection the items/count pipelines run against for the given strategy. */
  public static String collectionFor(Strategy s) {
    return s == Strategy.PEOPLE_FIRST ? "title_principals" : "search_titles";
  }

  public static List<Document> items(
      TitleSearchFilter f, TitleSort sort, PageArgs page, List<String> allTitleTypes, SearchProperties props) {
    List<Document> p = baseStages(f, allTitleTypes, props);
    p.add(new Document("$sort", sortSpec(f, sort, strategyFor(f))));
    if (page.offset() > 0) {
      p.add(new Document("$skip", page.offset()));
    }
    p.add(new Document("$limit", page.limit()));
    p.add(new Document("$project", new Document("_id", 0).append("tconst", "$_id")));
    return p;
  }

  public static List<Document> count(
      TitleSearchFilter f, TitleSort sort, List<String> allTitleTypes, SearchProperties props) {
    List<Document> p = baseStages(f, allTitleTypes, props);
    p.add(new Document("$limit", props.countCap() + 1));
    p.add(new Document("$count", "n"));
    return p;
  }

  /** Shared match/join stages, up to (excluding) the final sort/page. Also the facet base. */
  public static List<Document> baseStages(
      TitleSearchFilter f, List<String> allTitleTypes, SearchProperties props) {
    return switch (strategyFor(f)) {
      case PLAIN -> plainBase(f, allTitleTypes, props);
      case PEOPLE_FIRST -> peopleFirstBase(f, allTitleTypes);
      case TEXT_FIRST -> textFirstBase(f, allTitleTypes, props);
    };
  }

  private static List<Document> plainBase(
      TitleSearchFilter f, List<String> allTitleTypes, SearchProperties props) {
    if (hasPrefix(f) && !f.hasText()) {
      // prefix-led: match ONLY the prefix first (hinted to title_prefix), take a
      // deterministic alphabetical candidate slice, then apply remaining filters.
      // Bounds the popularity top-k sort for hot short prefixes ("the...").
      List<Document> p = new ArrayList<>();
      p.add(new Document("$match", new Document("primaryTitleLower",
          new Document("$regex", Regexes.prefix(f.titlePrefix())))));
      p.add(new Document("$limit", props.prefixCandidateCap()));
      Document rest = titleMatch(f, allTitleTypes, false, false);
      if (!rest.isEmpty()) {
        p.add(new Document("$match", rest));
      }
      return p;
    }
    List<Document> p = new ArrayList<>();
    p.add(new Document("$match", titleMatch(f, allTitleTypes, true)));
    if (f.hasText()) {
      p.add(new Document("$addFields", new Document("score", new Document("$meta", "textScore"))));
    }
    return p;
  }

  /** withPeople without text: enter via title_principals' nconst index. */
  private static List<Document> peopleFirstBase(TitleSearchFilter f, List<String> allTitleTypes) {
    List<String> people = List.copyOf(new LinkedHashSet<>(f.withPeople()));
    List<Document> p = new ArrayList<>();
    Document credits = new Document("nconst", new Document("$in", people));
    if (f.peopleCategories() != null && !f.peopleCategories().isEmpty()) {
      credits.append("category", new Document("$in", f.peopleCategories()));
    }
    p.add(new Document("$match", credits));
    p.add(new Document("$group",
        new Document("_id", "$tconst").append("people", new Document("$addToSet", "$nconst"))));
    if (f.peopleModeOrDefault() == PeopleMatchMode.ALL) {
      p.add(new Document("$match", new Document("$expr",
          new Document("$eq", List.of(new Document("$size", "$people"), people.size())))));
    }
    p.add(new Document("$lookup", new Document("from", "search_titles")
        .append("localField", "_id").append("foreignField", "_id").append("as", "t")));
    p.add(new Document("$unwind", "$t"));
    p.add(new Document("$replaceRoot", new Document("newRoot", "$t")));
    p.add(new Document("$match", titleMatch(f, allTitleTypes, false)));
    return p;
  }

  /** query + withPeople: $text must lead, so cap text candidates then join principals. */
  private static List<Document> textFirstBase(
      TitleSearchFilter f, List<String> allTitleTypes, SearchProperties props) {
    List<String> people = List.copyOf(new LinkedHashSet<>(f.withPeople()));
    List<Document> p = new ArrayList<>();
    p.add(new Document("$match", titleMatch(f, allTitleTypes, true)));
    p.add(new Document("$addFields", new Document("score", new Document("$meta", "textScore"))));
    p.add(new Document("$sort", new Document("score", -1).append("_id", 1)));
    p.add(new Document("$limit", props.textCandidateCap()));
    Document creditMatch = new Document("$match",
        new Document("$expr", new Document("$and", List.of(
            new Document("$eq", List.of("$tconst", "$$tid")),
            new Document("$in", List.of("$nconst", people))))));
    if (f.peopleCategories() != null && !f.peopleCategories().isEmpty()) {
      creditMatch.get("$match", Document.class)
          .append("category", new Document("$in", f.peopleCategories()));
    }
    p.add(new Document("$lookup", new Document("from", "title_principals")
        .append("let", new Document("tid", "$_id"))
        .append("pipeline", List.of(creditMatch,
            new Document("$project", new Document("_id", 0).append("nconst", 1))))
        .append("as", "p")));
    if (f.peopleModeOrDefault() == PeopleMatchMode.ALL) {
      p.add(new Document("$match", new Document("$expr", new Document("$eq", List.of(
          new Document("$size", new Document("$setUnion", List.of("$p.nconst", List.of()))),
          people.size())))));
    } else {
      p.add(new Document("$match", new Document("p.0", new Document("$exists", true))));
    }
    return p;
  }

  static Document titleMatch(TitleSearchFilter f, List<String> allTitleTypes, boolean includeText) {
    return titleMatch(f, allTitleTypes, includeText, true);
  }

  /** Filter over search_titles fields; text/prefix included only when requested. */
  static Document titleMatch(TitleSearchFilter f, List<String> allTitleTypes,
      boolean includeText, boolean includePrefix) {
    Document m = new Document();
    if (includeText && f.hasText()) {
      m.append("$text", new Document("$search", f.query()));
    }
    if (includePrefix && hasPrefix(f)) {
      m.append("primaryTitleLower", new Document("$regex", Regexes.prefix(f.titlePrefix())));
    }
    List<String> types = (f.titleTypes() != null && !f.titleTypes().isEmpty())
        ? f.titleTypes() : allTitleTypes;
    if (types != null && !types.isEmpty()) {
      m.append("titleType", new Document("$in", types));
    }
    Document genres = new Document();
    if (f.genresAny() != null && !f.genresAny().isEmpty()) {
      genres.append("$in", f.genresAny());
    }
    if (f.genresAll() != null && !f.genresAll().isEmpty()) {
      genres.append("$all", f.genresAll());
    }
    if (!genres.isEmpty()) {
      m.append("genres", genres);
    }
    appendRange(m, "startYear", f.startYearFrom(), f.startYearTo());
    appendRange(m, "runtimeMinutes", f.runtimeFrom(), f.runtimeTo());
    appendRange(m, "averageRating", f.ratingFrom(), f.ratingTo());
    appendRange(m, "numVotes", f.votesFrom(), null);
    if (!f.adultIncluded()) {
      m.append("isAdult", false);
    }
    return m;
  }

  static void appendRange(Document m, String field, Object from, Object to) {
    Document range = new Document();
    if (from != null) {
      range.append("$gte", from);
    }
    if (to != null) {
      range.append("$lte", to);
    }
    if (!range.isEmpty()) {
      m.append(field, range);
    }
  }

  /**
   * The _id tiebreak direction must pair with the SearchIndexes sort families:
   * descending sorts run the index forward (_id:1), ascending ones stream the
   * same index backwards, which negates every key ({startYear:-1,_id:1} read in
   * reverse is {startYear:1,_id:-1}). Still deterministic either way.
   */
  static Document sortSpec(TitleSearchFilter f, TitleSort sort, Strategy strategy) {
    TitleSort effective = sort;
    if (sort == TitleSort.RELEVANCE) {
      effective = f.hasText() ? null : TitleSort.POPULARITY_DESC;
    }
    if (effective == null) {
      // score is materialized via $addFields after $text; in-memory sort
      return new Document("score", -1).append("_id", 1);
    }
    return switch (effective) {
      case POPULARITY_DESC -> new Document("numVotes", -1).append("_id", 1);
      case RATING_DESC -> new Document("averageRating", -1).append("_id", 1);
      case YEAR_DESC -> new Document("startYear", -1).append("_id", 1);
      case YEAR_ASC -> new Document("startYear", 1).append("_id", -1);
      case RELEVANCE -> throw new IllegalStateException("resolved above");
    };
  }
}

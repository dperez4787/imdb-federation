package org.perez_f_daniel.imdb.orchestrator.search;

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.NameSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.NameSort;

/**
 * Pure pipeline builders for name search. Strategies:
 * UNSCOPED       — direct search_names query (no title dimension)
 * PRINCIPALS_FIRST — inTitles given: enter via principals' tconst index
 * TITLES_FIRST   — open-ended title scope (inGenres/activeFrom/To): top-N popular
 *                  matching titles (deterministic cap) then join principals
 */
public final class NamePipelines {

  public enum Strategy { UNSCOPED, PRINCIPALS_FIRST, TITLES_FIRST }

  private NamePipelines() {}

  public static Strategy strategyFor(NameSearchFilter f) {
    if (!f.titleScoped()) {
      return Strategy.UNSCOPED;
    }
    return f.hasInTitles() ? Strategy.PRINCIPALS_FIRST : Strategy.TITLES_FIRST;
  }

  /** Same planner problem as titles: pin prefix-led unscoped queries to the prefix index. */
  public static java.util.Optional<String> hintFor(NameSearchFilter f) {
    return strategyFor(f) == Strategy.UNSCOPED && hasPrefix(f) && !f.hasText()
        ? java.util.Optional.of("name_prefix")
        : java.util.Optional.empty();
  }

  private static boolean hasPrefix(NameSearchFilter f) {
    return f.namePrefix() != null && !f.namePrefix().isBlank();
  }

  public static String collectionFor(Strategy s) {
    return switch (s) {
      case UNSCOPED -> "search_names";
      case PRINCIPALS_FIRST -> "title_principals";
      case TITLES_FIRST -> "search_titles";
    };
  }

  public static List<Document> items(NameSearchFilter f, NameSort sort, PageArgs page,
      List<String> resolvedInTitles, SearchProperties props) {
    List<Document> p = baseStages(f, resolvedInTitles, props);
    p.add(new Document("$sort", sortSpec(f, sort, strategyFor(f))));
    if (page.offset() > 0) {
      p.add(new Document("$skip", page.offset()));
    }
    p.add(new Document("$limit", page.limit()));
    p.add(new Document("$project", new Document("_id", 0).append("nconst",
        strategyFor(f) == Strategy.UNSCOPED ? "$_id" : "$_id")));
    return p;
  }

  public static List<Document> count(NameSearchFilter f, List<String> resolvedInTitles,
      SearchProperties props) {
    List<Document> p = baseStages(f, resolvedInTitles, props);
    p.add(new Document("$limit", props.countCap() + 1));
    p.add(new Document("$count", "n"));
    return p;
  }

  /** Capped count of the open-ended title scope, to report titleCandidatesCapped. */
  public static List<Document> titleScopeCount(NameSearchFilter f, SearchProperties props) {
    List<Document> p = new ArrayList<>();
    p.add(new Document("$match", titleScopeMatch(f)));
    p.add(new Document("$limit", props.titleCandidateCap() + 1));
    p.add(new Document("$count", "n"));
    return p;
  }

  /** Shared match/join stages, up to (excluding) the final sort/page. Also the facet base. */
  public static List<Document> baseStages(NameSearchFilter f, List<String> resolvedInTitles,
      SearchProperties props) {
    return switch (strategyFor(f)) {
      case UNSCOPED -> unscopedBase(f, props);
      case PRINCIPALS_FIRST -> {
        List<Document> p = principalsEntry(f, resolvedInTitles);
        p.addAll(joinTail(f));
        yield p;
      }
      case TITLES_FIRST -> {
        List<Document> p = titlesFirstEntry(f, props);
        p.addAll(joinTail(f));
        yield p;
      }
    };
  }

  private static List<Document> unscopedBase(NameSearchFilter f, SearchProperties props) {
    List<Document> p = new ArrayList<>();
    if (hasPrefix(f) && !f.hasText()) {
      // prefix-led: hinted prefix match, deterministic alphabetical candidate
      // slice, then remaining filters (see TitlePipelines.plainBase rationale)
      p.add(new Document("$match", new Document("primaryNameLower",
          new Document("$regex", Regexes.prefix(f.namePrefix())))));
      p.add(new Document("$limit", props.prefixCandidateCap()));
      Document rest = new Document();
      appendNameFilters(rest, f, "", false);
      if (!rest.isEmpty()) {
        p.add(new Document("$match", rest));
      }
      return p;
    }
    Document m = new Document();
    if (f.hasText()) {
      m.append("$text", new Document("$search", f.query()));
    }
    appendNameFilters(m, f, "", true);
    p.add(new Document("$match", m));
    if (f.hasText()) {
      p.add(new Document("$addFields", new Document("score", new Document("$meta", "textScore"))));
    }
    return p;
  }

  private static List<Document> principalsEntry(NameSearchFilter f, List<String> tconsts) {
    List<Document> p = new ArrayList<>();
    Document m = new Document("tconst", new Document("$in", tconsts));
    if (f.categories() != null && !f.categories().isEmpty()) {
      m.append("category", new Document("$in", f.categories()));
    }
    p.add(new Document("$match", m));
    p.add(new Document("$group",
        new Document("_id", "$nconst").append("credits", new Document("$sum", 1))));
    return p;
  }

  private static List<Document> titlesFirstEntry(NameSearchFilter f, SearchProperties props) {
    List<Document> p = new ArrayList<>();
    p.add(new Document("$match", titleScopeMatch(f)));
    // deterministic cap: keep the MOST POPULAR matching titles
    p.add(new Document("$sort", new Document("numVotes", -1).append("_id", 1)));
    p.add(new Document("$limit", props.titleCandidateCap()));
    p.add(new Document("$project", new Document("_id", 1)));
    List<Document> creditPipeline = new ArrayList<>();
    if (f.categories() != null && !f.categories().isEmpty()) {
      creditPipeline.add(new Document("$match",
          new Document("category", new Document("$in", f.categories()))));
    }
    creditPipeline.add(new Document("$project", new Document("_id", 0).append("nconst", 1)));
    p.add(new Document("$lookup", new Document("from", "title_principals")
        .append("localField", "_id").append("foreignField", "tconst")
        .append("pipeline", creditPipeline).append("as", "p")));
    p.add(new Document("$unwind", "$p"));
    p.add(new Document("$group",
        new Document("_id", "$p.nconst").append("credits", new Document("$sum", 1))));
    return p;
  }

  /** After the {_id: nconst, credits} shape: join search_names + name-level filters. */
  private static List<Document> joinTail(NameSearchFilter f) {
    List<Document> p = new ArrayList<>();
    p.add(new Document("$lookup", new Document("from", "search_names")
        .append("localField", "_id").append("foreignField", "_id").append("as", "n")));
    p.add(new Document("$unwind", "$n"));
    Document m = new Document();
    appendNameFilters(m, f, "n.", true);
    if (f.hasText()) {
      // degraded text: substring over the small joined candidate set
      m.append("n.primaryNameLower", new Document("$regex", Regexes.substring(f.query())));
    }
    if (!m.isEmpty()) {
      p.add(new Document("$match", m));
    }
    return p;
  }

  /** Name-level filters shared by both shapes; prefix inclusion controlled by caller. */
  private static void appendNameFilters(Document m, NameSearchFilter f, String pfx, boolean includePrefix) {
    if (includePrefix && hasPrefix(f)) {
      m.append(pfx + "primaryNameLower", new Document("$regex", Regexes.prefix(f.namePrefix())));
    }
    if (f.professions() != null && !f.professions().isEmpty()) {
      m.append(pfx + "professions", new Document("$in", f.professions()));
    }
    TitlePipelines.appendRange(m, pfx + "birthYear", f.bornFrom(), f.bornTo());
  }

  /** Title-side scope for TITLES_FIRST and the candidates-capped check. */
  static Document titleScopeMatch(NameSearchFilter f) {
    Document m = new Document();
    if (f.inGenres() != null && !f.inGenres().isEmpty()) {
      m.append("genres", new Document("$in", f.inGenres()));
    }
    TitlePipelines.appendRange(m, "startYear", f.activeFrom(), f.activeTo());
    return m;
  }

  static Document sortSpec(NameSearchFilter f, NameSort sort, Strategy strategy) {
    boolean scoped = strategy != Strategy.UNSCOPED;
    NameSort effective = sort;
    if (sort == NameSort.RELEVANCE) {
      if (f.hasText() && !scoped) {
        return new Document("score", -1).append("_id", 1);
      }
      effective = scoped ? NameSort.CREDITS_DESC : NameSort.POPULARITY_DESC;
    }
    String namePath = scoped ? "n." : "";
    Document s = switch (effective) {
      case POPULARITY_DESC -> new Document(namePath + "popularity", -1);
      case CREDITS_DESC -> new Document("credits", -1);
      case NAME_ASC -> new Document(namePath + "primaryNameLower", 1);
      case BIRTH_YEAR_ASC -> new Document(namePath + "birthYear", 1);
      case BIRTH_YEAR_DESC -> new Document(namePath + "birthYear", -1);
      case RELEVANCE -> throw new IllegalStateException("resolved above");
    };
    return s.append("_id", 1);
  }
}

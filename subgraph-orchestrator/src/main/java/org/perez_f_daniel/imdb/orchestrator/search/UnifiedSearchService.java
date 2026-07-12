package org.perez_f_daniel.imdb.orchestrator.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.perez_f_daniel.imdb.orchestrator.api.Name;
import org.perez_f_daniel.imdb.orchestrator.api.SearchKind;
import org.perez_f_daniel.imdb.orchestrator.api.Title;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * The global search box: every query token must match (AND) against the
 * titleTerms/nameTerms arrays materialized at rebuild, ranked by popularity —
 * numVotes (titles) / materialized knownFor votes (names); absent sorts last.
 * The trailing token of a multi-token query may be partially typed, so it also
 * prefix-matches when it has minPrefixLength+ chars. A lone token matches
 * exactly: a point bound streams the {terms, popularity} index and terminates
 * at the limit, where a bare prefix range over a common token ("the") would
 * top-k-sort its whole index range. This replaced the $text ranking — $text is
 * OR-semantics, so one common token ("la") scored millions of docs and hit the
 * cluster's execution ceiling.
 */
@Service
public class UnifiedSearchService {

  /** stub is the GraphQL union member (Title or Name key stub). */
  record Hit(Object stub, long popularity, String id) {}

  private static final Comparator<Hit> RANK = Comparator
      .comparingLong(Hit::popularity).reversed()
      .thenComparing(Hit::id);

  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

  private final MongoTemplate mongo;
  private final SearchProperties props;

  public UnifiedSearchService(MongoTemplate mongo, SearchProperties props) {
    this.mongo = mongo;
    this.props = props;
  }

  public List<Object> search(String query, Set<SearchKind> kinds, int limit) {
    List<String> tokens = tokenize(query);
    if (tokens.isEmpty()) {
      return List.of();
    }
    List<Hit> hits = new ArrayList<>(limit * 2);
    if (kinds.contains(SearchKind.TITLE)) {
      Document match = termsMatch("titleTerms", tokens, props.minPrefixLength())
          .append("isAdult", false);
      for (Document d : termsQuery("search_titles", match, "numVotes", limit)) {
        hits.add(new Hit(new Title(d.getString("_id")),
            popularity(d, "numVotes"), d.getString("_id")));
      }
    }
    if (kinds.contains(SearchKind.NAME)) {
      Document match = termsMatch("nameTerms", tokens, props.minPrefixLength());
      for (Document d : termsQuery("search_names", match, "popularity", limit)) {
        hits.add(new Hit(new Name(d.getString("_id")),
            popularity(d, "popularity"), d.getString("_id")));
      }
    }
    return hits.stream().sorted(RANK).limit(limit).map(Hit::stub).toList();
  }

  private Iterable<Document> termsQuery(
      String collection, Document match, String popularityField, int limit) {
    return mongo.getCollection(collection).find(match)
        .projection(new Document(popularityField, 1))
        .sort(new Document(popularityField, -1).append("_id", 1))
        .limit(limit)
        .maxTime(props.queryTimeoutMs(), TimeUnit.MILLISECONDS);
  }

  /**
   * Query-side twin of the rebuild's terms materialization
   * (RebuildService.termsExpr): ASCII-only lowercasing (mirroring $toLower),
   * then [\p{L}\p{N}]+ runs. Order-preserving; duplicates kept so the trailing
   * token is always the one the user typed last.
   */
  public static List<String> tokenize(String query) {
    StringBuilder lower = new StringBuilder(query.length());
    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      lower.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
    }
    List<String> tokens = new ArrayList<>();
    Matcher m = TOKEN.matcher(lower);
    while (m.find()) {
      tokens.add(m.group());
    }
    return tokens;
  }

  /**
   * One equality clause per distinct token, the trailing token as an anchored
   * prefix when the query has more tokens and it is long enough to bound a
   * selective index range. The planner picks bounds from whichever clause is
   * cheapest and applies the rest as residual filters.
   */
  public static Document termsMatch(String field, List<String> tokens, int minPrefixLength) {
    String trailing = tokens.get(tokens.size() - 1);
    boolean prefixTrailing = tokens.size() > 1 && trailing.length() >= minPrefixLength;
    Set<String> exact = new LinkedHashSet<>(
        prefixTrailing ? tokens.subList(0, tokens.size() - 1) : tokens);
    List<Document> clauses = new ArrayList<>();
    for (String token : exact) {
      clauses.add(new Document(field, token));
    }
    if (prefixTrailing) {
      clauses.add(new Document(field,
          new Document("$regex", "^" + Regexes.escape(trailing))));
    }
    return clauses.size() == 1 ? clauses.get(0) : new Document("$and", clauses);
  }

  private static long popularity(Document d, String field) {
    Number n = d.get(field, Number.class);
    return n == null ? 0L : n.longValue();
  }
}

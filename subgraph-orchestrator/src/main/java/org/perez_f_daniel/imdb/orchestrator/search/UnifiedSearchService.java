package org.perez_f_daniel.imdb.orchestrator.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.perez_f_daniel.imdb.orchestrator.api.Name;
import org.perez_f_daniel.imdb.orchestrator.api.SearchKind;
import org.perez_f_daniel.imdb.orchestrator.api.Title;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * The global search box: one $text query per requested kind, merged and ranked
 * by text score with popularity breaking ties. Scores from the two text indexes
 * are tf-idf-alike and treated as comparable — a pragmatic global-box ranking,
 * not a probability. Popularity: numVotes (titles) / materialized knownFor votes
 * (names); absent sorts last.
 */
@Service
public class UnifiedSearchService {

  /** stub is the GraphQL union member (Title or Name key stub). */
  record Hit(Object stub, double score, long popularity, String id) {}

  private static final Comparator<Hit> RANK = Comparator
      .comparingDouble(Hit::score).reversed()
      .thenComparing(Comparator.comparingLong(Hit::popularity).reversed())
      .thenComparing(Hit::id);

  private final MongoTemplate mongo;
  private final SearchProperties props;

  public UnifiedSearchService(MongoTemplate mongo, SearchProperties props) {
    this.mongo = mongo;
    this.props = props;
  }

  public List<Object> search(String query, Set<SearchKind> kinds, int limit) {
    List<Hit> hits = new ArrayList<>(limit * 2);
    if (kinds.contains(SearchKind.TITLE)) {
      for (Document d : textQuery("search_titles", query, "numVotes",
          new Document("isAdult", false), limit)) {
        hits.add(new Hit(new Title(d.getString("_id")), score(d), popularity(d), d.getString("_id")));
      }
    }
    if (kinds.contains(SearchKind.NAME)) {
      for (Document d : textQuery("search_names", query, "popularity", new Document(), limit)) {
        hits.add(new Hit(new Name(d.getString("_id")), score(d), popularity(d), d.getString("_id")));
      }
    }
    return hits.stream().sorted(RANK).limit(limit).map(Hit::stub).toList();
  }

  private Iterable<Document> textQuery(
      String collection, String query, String popularityField, Document extraMatch, int limit) {
    Document match = new Document("$text", new Document("$search", query));
    match.putAll(extraMatch);
    List<Document> pipeline = List.of(
        new Document("$match", match),
        new Document("$addFields", new Document()
            .append("score", new Document("$meta", "textScore"))
            .append("pop", new Document("$ifNull", java.util.Arrays.asList("$" + popularityField, 0L)))),
        new Document("$sort", new Document("score", -1).append("pop", -1).append("_id", 1)),
        new Document("$limit", limit),
        new Document("$project", new Document("score", 1).append("pop", 1)));
    return mongo.getCollection(collection).aggregate(pipeline).allowDiskUse(true)
        .maxTime(props.queryTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  private static double score(Document d) {
    Number n = d.get("score", Number.class);
    return n == null ? 0d : n.doubleValue();
  }

  private static long popularity(Document d) {
    Number n = d.get("pop", Number.class);
    return n == null ? 0L : n.longValue();
  }
}

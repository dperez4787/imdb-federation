package org.perez_f_daniel.imdb.orchestrator.rebuild;

import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import java.util.List;
import org.bson.Document;

/**
 * Index contract for the derived search collections. Names are stable so
 * createIndexes stays idempotent (same name + same keys = no-op).
 * ESR-designed, one compound per (equality, sort) family — the planner
 * effectively never index-intersects.
 */
public final class SearchIndexes {

  private SearchIndexes() {}

  public static final List<IndexModel> SEARCH_TITLES = List.of(
      idx("type_votes_year", new Document("titleType", 1).append("numVotes", -1).append("startYear", 1)),
      idx("type_rating_votes", new Document("titleType", 1).append("averageRating", -1).append("numVotes", 1)),
      idx("type_year", new Document("titleType", 1).append("startYear", -1)),
      idx("genres_type_votes_year",
          new Document("genres", 1).append("titleType", 1).append("numVotes", -1).append("startYear", 1)),
      idx("genres_type_rating_votes",
          new Document("genres", 1).append("titleType", 1).append("averageRating", -1).append("numVotes", 1)),
      idx("genres_type_year",
          new Document("genres", 1).append("titleType", 1).append("startYear", -1)),
      idx("title_prefix", new Document("primaryTitleLower", 1)),
      idx("title_text", new Document("primaryTitle", "text")));

  public static final List<IndexModel> SEARCH_NAMES = List.of(
      idx("professions_popularity", new Document("professions", 1).append("popularity", -1)),
      idx("popularity", new Document("popularity", -1)),
      idx("professions_born", new Document("professions", 1).append("birthYear", 1)),
      new IndexModel(new Document("birthYear", 1), new IndexOptions()
          .name("born")
          .partialFilterExpression(new Document("birthYear", new Document("$exists", true)))),
      idx("name_prefix", new Document("primaryNameLower", 1)),
      idx("name_text", new Document("primaryName", "text")));

  private static IndexModel idx(String name, Document keys) {
    return new IndexModel(keys, new IndexOptions().name(name));
  }
}

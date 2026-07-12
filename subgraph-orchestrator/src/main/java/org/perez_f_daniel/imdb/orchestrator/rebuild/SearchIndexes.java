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
 *
 * Every sort-family index ends with the exact sort keys INCLUDING the _id
 * tiebreak that the pipelines' sortSpec appends: an index can only stream a
 * sort when all sort keys appear in it in order, so an index without the _id
 * tail forces a blocking sort over the whole filtered set (12M+ docs — killed
 * by the cluster's execution time limit). Ascending sort variants reuse the
 * descending index via reverse traversal, which is why sortSpec flips the
 * tiebreak to _id:-1 for them. The bare sort indexes (votes_id, rating_id,
 * year_id, popularity_id) give the planner a streaming option when the
 * remaining filters are unselective residuals (e.g. genre browse), while the
 * prefixed families win for selective equality filters.
 */
public final class SearchIndexes {

  private SearchIndexes() {}

  public static final List<IndexModel> SEARCH_TITLES = List.of(
      idx("type_votes_id", new Document("titleType", 1).append("numVotes", -1).append("_id", 1)),
      idx("type_rating_id", new Document("titleType", 1).append("averageRating", -1).append("_id", 1)),
      idx("type_year_id", new Document("titleType", 1).append("startYear", -1).append("_id", 1)),
      idx("genres_type_votes_id",
          new Document("genres", 1).append("titleType", 1).append("numVotes", -1).append("_id", 1)),
      idx("genres_type_rating_id",
          new Document("genres", 1).append("titleType", 1).append("averageRating", -1).append("_id", 1)),
      idx("genres_type_year_id",
          new Document("genres", 1).append("titleType", 1).append("startYear", -1).append("_id", 1)),
      idx("votes_id", new Document("numVotes", -1).append("_id", 1)),
      idx("rating_id", new Document("averageRating", -1).append("_id", 1)),
      idx("year_id", new Document("startYear", -1).append("_id", 1)),
      idx("title_prefix", new Document("primaryTitleLower", 1)),
      idx("title_text", new Document("primaryTitle", "text")));

  public static final List<IndexModel> SEARCH_NAMES = List.of(
      idx("professions_popularity_id",
          new Document("professions", 1).append("popularity", -1).append("_id", 1)),
      idx("popularity_id", new Document("popularity", -1).append("_id", 1)),
      idx("professions_born_id",
          new Document("professions", 1).append("birthYear", 1).append("_id", 1)),
      // full (not partial): unfiltered BIRTH_YEAR_* sorts must stream the
      // missing-birthYear docs too, and a partial index can't serve them
      idx("born_id", new Document("birthYear", 1).append("_id", 1)),
      idx("name_prefix_id", new Document("primaryNameLower", 1).append("_id", 1)),
      idx("name_text", new Document("primaryName", "text")));

  private static IndexModel idx(String name, Document keys) {
    return new IndexModel(keys, new IndexOptions().name(name));
  }
}

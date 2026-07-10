package org.perez_f_daniel.imdb.orchestrator.rebuild;

import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.Updates;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.perez_f_daniel.imdb.orchestrator.search.TitleTypesCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Rebuilds the derived search collections from the pipeline-owned source
 * collections. Build into *_next, index, then atomically rename over the live
 * names (mirroring the import pipeline's own staging->promote pattern). Any
 * failure leaves the live collections untouched.
 */
@Service
public class RebuildService {

  /** Declared execution order; ?steps= subsets must respect it. FACETS reads the LIVE (promoted) collections. */
  public enum Step { TITLES, RATINGS, NAMES, KFT, POPULARITY, INDEXES, PROMOTE, FACETS }

  public static final class RebuildLockedException extends RuntimeException {
    RebuildLockedException() {
      super("a rebuild is already running");
    }
  }

  private static final Logger log = LoggerFactory.getLogger(RebuildService.class);
  private static final Duration STALE_LOCK = Duration.ofHours(2);

  private final MongoTemplate mongo;
  private final MongoClient client;
  private final TitleTypesCache titleTypes;

  public RebuildService(MongoTemplate mongo, MongoClient client, TitleTypesCache titleTypes) {
    this.mongo = mongo;
    this.client = client;
    this.titleTypes = titleTypes;
  }

  /** Runs the requested steps in declared order; returns per-step durations (ms). */
  public Map<String, Object> run(List<Step> steps) {
    return run(steps, null);
  }

  /**
   * Step-driven rebuilds release the request lock between steps, so a bare lock
   * would let a second driver interleave into an in-progress sequence and clobber
   * the *_next staging collections. A runId claims a SESSION that persists across
   * the driver's step requests: requests carrying a different (or no) runId get
   * 409 until the session closes — on the terminal FACETS step, on failure, or
   * after 2h of inactivity. Requests without a runId behave as one-shot runs.
   */
  public Map<String, Object> run(List<Step> steps, String runId) {
    acquireLock(runId);
    Instant started = Instant.now();
    Map<String, Object> report = new LinkedHashMap<>();
    try {
      for (Step step : Step.values()) {
        if (!steps.contains(step)) {
          continue;
        }
        Instant t0 = Instant.now();
        runStep(step);
        report.put(step.name().toLowerCase(), Duration.between(t0, Instant.now()).toMillis());
        log.info("[rebuild] {} done in {}ms", step, report.get(step.name().toLowerCase()));
      }
      boolean closesSession = runId == null || steps.contains(Step.FACETS);
      releaseLock(new Document("status", "IDLE")
          .append("finishedAt", Instant.now())
          .append("lastActivityAt", Instant.now())
          .append("durationMs", Duration.between(started, Instant.now()).toMillis())
          .append("error", null)
          .append("runId", closesSession ? null : runId));
      report.put("status", "OK");
      if (runId != null) {
        report.put("runId", runId);
        report.put("sessionOpen", !closesSession);
      }
      return report;
    } catch (RuntimeException e) {
      // failure closes the session so a fresh run can start immediately
      releaseLock(new Document("status", "FAILED")
          .append("finishedAt", Instant.now())
          .append("lastActivityAt", Instant.now())
          .append("error", String.valueOf(e.getMessage()))
          .append("runId", null));
      throw e;
    }
  }

  private void runStep(Step step) {
    MongoDatabase db = mongo.getDb();
    switch (step) {
      case TITLES -> db.getCollection("title_basics").aggregate(List.of(
          new Document("$project", new Document()
              .append("_id", "$tconst")
              .append("titleType", 1)
              .append("primaryTitle", 1)
              .append("primaryTitleLower", new Document("$toLower", "$primaryTitle"))
              .append("startYear", 1)
              .append("endYear", 1)
              .append("runtimeMinutes", 1)
              .append("isAdult", new Document("$eq", List.of("$isAdult", 1)))
              .append("genres", splitOrRemove("$genres"))),
          new Document("$out", "search_titles_next"))).allowDiskUse(true).toCollection();

      case RATINGS -> db.getCollection("title_ratings").aggregate(List.of(
          new Document("$project", new Document()
              .append("_id", "$tconst")
              .append("averageRating", 1)
              .append("numVotes", 1)),
          new Document("$merge", new Document("into", "search_titles_next")
              .append("on", "_id")
              .append("whenMatched", "merge")
              .append("whenNotMatched", "discard")))).allowDiskUse(true).toCollection();

      case NAMES -> db.getCollection("name_basics").aggregate(List.of(
          new Document("$project", new Document()
              .append("_id", "$nconst")
              .append("primaryName", 1)
              .append("primaryNameLower", new Document("$toLower", "$primaryName"))
              .append("birthYear", 1)
              .append("deathYear", 1)
              .append("professions", splitOrRemove("$primaryProfession"))),
          new Document("$out", "search_names_next"))).allowDiskUse(true).toCollection();

      case KFT -> {
        db.getCollection("name_basics").aggregate(List.of(
            new Document("$project", new Document()
                .append("_id", 0)
                .append("nconst", 1)
                .append("kft", new Document("$cond", List.of(
                    new Document("$gt", java.util.Arrays.asList("$knownForTitles", null)),
                    new Document("$split", List.of("$knownForTitles", ",")),
                    List.of())))),
            new Document("$unwind", "$kft"),
            new Document("$project", new Document("nconst", 1).append("tconst", "$kft")),
            new Document("$out", "tmp_kft"))).allowDiskUse(true).toCollection();
        db.getCollection("tmp_kft")
            .createIndexes(List.of(new IndexModel(new Document("tconst", 1))));
      }

      case POPULARITY -> {
        db.getCollection("search_titles_next").aggregate(List.of(
            new Document("$match", new Document("numVotes", new Document("$exists", true))),
            new Document("$project", new Document("numVotes", 1)),
            new Document("$lookup", new Document("from", "tmp_kft")
                .append("localField", "_id").append("foreignField", "tconst").append("as", "k")),
            new Document("$unwind", "$k"),
            new Document("$group", new Document("_id", "$k.nconst")
                .append("popularity", new Document("$sum", "$numVotes"))),
            new Document("$merge", new Document("into", "search_names_next")
                .append("on", "_id")
                .append("whenMatched", "merge")
                .append("whenNotMatched", "discard")))).allowDiskUse(true).toCollection();
        db.getCollection("tmp_kft").drop();
      }

      case INDEXES -> {
        db.getCollection("search_titles_next").createIndexes(SearchIndexes.SEARCH_TITLES);
        db.getCollection("search_names_next").createIndexes(SearchIndexes.SEARCH_NAMES);
      }

      case PROMOTE -> {
        List<String> types = db.getCollection("search_titles_next")
            .distinct("titleType", String.class).into(new ArrayList<>());
        long titleCount = db.getCollection("search_titles_next").estimatedDocumentCount();
        long nameCount = db.getCollection("search_names_next").estimatedDocumentCount();
        rename(db.getName(), "search_titles_next", "search_titles");
        rename(db.getName(), "search_names_next", "search_names");
        meta().updateOne(Filters.eq("_id", "rebuild"), Updates.combine(
            Updates.set("rebuiltAt", Instant.now()),
            Updates.set("titleCount", (int) Math.min(titleCount, Integer.MAX_VALUE)),
            Updates.set("nameCount", (int) Math.min(nameCount, Integer.MAX_VALUE)),
            Updates.set("titleTypes", types)));
        titleTypes.invalidate();
      }

      case FACETS -> {
        materializeFacet("genres", "search_titles",
            new Document("$unwind", "$genres"), "$genres");
        materializeFacet("titleTypes", "search_titles", null, "$titleType");
        materializeFacet("principalCategories", "title_principals",
            new Document("$match", new Document("category", new Document("$exists", true))),
            "$category");
        materializeFacet("professions", "search_names",
            new Document("$unwind", "$professions"), "$professions");
        materializeFacet("akaRegions", "title_akas",
            new Document("$match", new Document("region", new Document("$exists", true))),
            "$region");
        materializeFacet("akaLanguages", "title_akas",
            new Document("$match", new Document("language", new Document("$exists", true))),
            "$language");
      }
    }
  }

  private static final int FACET_VALUE_CAP = 200;

  /** One value-count pass per vocabulary, upserted into search_facets. */
  private void materializeFacet(String id, String collection, Document preStage, String groupExpr) {
    List<Document> pipeline = new ArrayList<>();
    if (preStage != null) {
      pipeline.add(preStage);
    }
    pipeline.add(new Document("$group",
        new Document("_id", groupExpr).append("count", new Document("$sum", 1))));
    pipeline.add(new Document("$sort", new Document("count", -1).append("_id", 1)));
    pipeline.add(new Document("$limit", FACET_VALUE_CAP));
    pipeline.add(new Document("$project", new Document("_id", 0)
        .append("value", "$_id").append("count", 1)));
    List<Document> values = mongo.getDb().getCollection(collection)
        .aggregate(pipeline).allowDiskUse(true).into(new ArrayList<>());
    mongo.getDb().getCollection("search_facets").replaceOne(
        Filters.eq("_id", id),
        new Document("_id", id).append("values", values),
        new com.mongodb.client.model.ReplaceOptions().upsert(true));
  }

  /** {$split: [csv, ","]} when present, $$REMOVE when the source column was absent. */
  private static Document splitOrRemove(String field) {
    return new Document("$cond", List.of(
        new Document("$gt", java.util.Arrays.asList(field, null)),
        new Document("$split", List.of(field, ",")),
        "$$REMOVE"));
  }

  private void rename(String dbName, String from, String to) {
    client.getDatabase("admin").runCommand(new Document()
        .append("renameCollection", dbName + "." + from)
        .append("to", dbName + "." + to)
        .append("dropTarget", true));
  }

  private MongoCollection<Document> meta() {
    return mongo.getDb().getCollection("search_meta");
  }

  private void acquireLock(String runId) {
    Instant now = Instant.now();
    Instant stale = now.minus(STALE_LOCK);
    Bson notRunning = Filters.or(
        Filters.ne("status", "RUNNING"),
        Filters.lt("startedAt", stale));
    // session guard: same run, no open session, or session gone stale
    Bson sessionFree = Filters.or(
        Filters.eq("runId", runId),
        Filters.eq("runId", null),
        Filters.lt("lastActivityAt", stale));
    try {
      Document r = meta().findOneAndUpdate(
          Filters.and(Filters.eq("_id", "rebuild"), notRunning, sessionFree),
          Updates.combine(
              Updates.set("status", "RUNNING"),
              Updates.set("startedAt", now),
              Updates.set("lastActivityAt", now),
              Updates.set("runId", runId)),
          new FindOneAndUpdateOptions().upsert(true));
      // r may be null on first-ever upsert insert — that's a successful acquisition
    } catch (MongoWriteException | DuplicateKeyException e) {
      // upsert raced/blocked by a RUNNING request or another run's open session
      throw new RebuildLockedException();
    } catch (com.mongodb.MongoCommandException e) {
      if (e.getErrorCode() == 11000) {
        throw new RebuildLockedException();
      }
      throw e;
    }
  }

  private void releaseLock(Document fields) {
    Document set = new Document(fields);
    meta().updateOne(Filters.eq("_id", "rebuild"), new Document("$set", set));
  }
}

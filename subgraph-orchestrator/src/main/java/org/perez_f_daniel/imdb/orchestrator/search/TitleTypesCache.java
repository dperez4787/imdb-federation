package org.perez_f_daniel.imdb.orchestrator.search;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Distinct titleType values recorded by the last rebuild (search_meta). Injected
 * as an $in when the caller passes no titleTypes so the type-led compound indexes
 * stay usable (SORT_MERGE across per-type scans instead of a collection scan).
 */
@Component
public class TitleTypesCache {

  private static final Duration TTL = Duration.ofMinutes(10);

  private final MongoTemplate mongo;
  private final AtomicReference<Entry> cache = new AtomicReference<>();

  private record Entry(List<String> types, Instant loadedAt) {}

  public TitleTypesCache(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  public List<String> get() {
    Entry e = cache.get();
    if (e != null && e.loadedAt().plus(TTL).isAfter(Instant.now())) {
      return e.types();
    }
    Document meta = mongo.findById("rebuild", Document.class, "search_meta");
    List<String> types = meta == null ? List.of() : meta.getList("titleTypes", String.class, List.of());
    cache.set(new Entry(types, Instant.now()));
    return types;
  }

  public void invalidate() {
    cache.set(null);
  }
}

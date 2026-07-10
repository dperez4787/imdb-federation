package org.perez_f_daniel.imdb.common;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.dataloader.MappedBatchLoader;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Factories for the three DataLoader shapes every subgraph uses. All queries are
 * indexed {@code $in} lookups; Mongo driver calls are synchronous, so loaders run
 * on the dedicated executor rather than graphql-java's thread.
 */
public final class BatchLoaders {

  private BatchLoaders() {}

  /** One doc per key (unique index): N keys -> one {@code $in} find -> Map&lt;key, V&gt;. */
  public static <D, V> MappedBatchLoader<String, V> byUniqueKey(
      MongoTemplate mongo, Executor executor, Class<D> docClass, String keyField,
      Function<D, String> keyFn, Function<D, V> mapper) {
    return keys -> CompletableFuture.supplyAsync(() ->
        mongo.find(Query.query(Criteria.where(keyField).in(keys)), docClass).stream()
            .collect(Collectors.toMap(keyFn, mapper, (a, b) -> a)), executor);
  }

  /** All docs per key (non-unique index), sorted: N keys -> one {@code $in} find -> Map&lt;key, List&lt;V&gt;&gt;. */
  public static <D, V> MappedBatchLoader<String, List<V>> groupedByKey(
      MongoTemplate mongo, Executor executor, Class<D> docClass, String keyField, Sort sort,
      Function<D, String> keyFn, Function<D, V> mapper) {
    return keys -> CompletableFuture.supplyAsync(() ->
        mongo.find(Query.query(Criteria.where(keyField).in(keys)).with(sort), docClass).stream()
            .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new,
                Collectors.mapping(mapper, Collectors.toList()))), executor);
  }

  /**
   * Paged variant of {@link #groupedByKey}: one aggregation per distinct (limit, offset)
   * pair — $match (indexed) -> $sort -> $group/$push -> $slice — so large per-key groups
   * (e.g. 18k-episode soaps) never leave the server unsliced.
   */
  public static <D, V> MappedBatchLoader<GroupPage, List<V>> groupedPaged(
      MongoTemplate mongo, Executor executor, Class<D> docClass, String collection,
      String keyField, Sort sort, Function<D, V> mapper) {
    return pages -> CompletableFuture.supplyAsync(() -> {
      Map<GroupPage, List<V>> out = new HashMap<>();
      Map<PageArgs, List<GroupPage>> byPage =
          pages.stream().collect(Collectors.groupingBy(GroupPage::page));
      for (Map.Entry<PageArgs, List<GroupPage>> entry : byPage.entrySet()) {
        PageArgs page = entry.getKey();
        List<String> keys = entry.getValue().stream().map(GroupPage::key).toList();
        AggregationExpression slice =
            ctx -> new Document("$slice", List.of("$docs", page.offset(), page.limit()));
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(keyField).in(keys)),
                Aggregation.sort(sort),
                Aggregation.group(keyField).push("$$ROOT").as("docs"),
                Aggregation.project().and(slice).as("docs"))
            .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        for (Document group : mongo.aggregate(agg, collection, Document.class)) {
          List<Document> docs = group.getList("docs", Document.class);
          List<V> values = docs == null ? List.of()
              : docs.stream().map(d -> mapper.apply(mongo.getConverter().read(docClass, d))).toList();
          out.put(new GroupPage(group.getString("_id"), page.limit(), page.offset()), values);
        }
      }
      return out;
    }, executor);
  }
}

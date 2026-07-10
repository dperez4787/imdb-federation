package org.perez_f_daniel.imdb.orchestrator.api;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bson.Document;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.search.CountResult;
import org.perez_f_daniel.imdb.orchestrator.search.NameSearchService;
import org.perez_f_daniel.imdb.orchestrator.search.SearchProperties;
import org.perez_f_daniel.imdb.orchestrator.search.TitleSearchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsComponent
public class SearchDataFetchers {

  private final TitleSearchService titles;
  private final NameSearchService names;
  private final org.perez_f_daniel.imdb.orchestrator.search.UnifiedSearchService unified;
  private final FilterValidation validation;
  private final SearchProperties props;
  private final MongoTemplate mongo;
  private final Executor executor;

  public SearchDataFetchers(TitleSearchService titles, NameSearchService names,
      org.perez_f_daniel.imdb.orchestrator.search.UnifiedSearchService unified,
      FilterValidation validation, SearchProperties props, MongoTemplate mongo,
      @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.titles = titles;
    this.names = names;
    this.unified = unified;
    this.validation = validation;
    this.props = props;
    this.mongo = mongo;
    this.executor = executor;
  }

  /** Parent fetchers return a context; each result field runs its own (memoized) query lazily. */
  public final class TitleSearchContext {
    final TitleSearchFilter filter;
    final TitleSort sort;
    final PageArgs page;
    private final Supplier<CountResult> count;

    TitleSearchContext(TitleSearchFilter filter, TitleSort sort, PageArgs page) {
      this.filter = filter;
      this.sort = sort;
      this.page = page;
      this.count = memoize(() -> titles.count(filter, sort));
    }

    CountResult count() {
      return count.get();
    }
  }

  public final class NameSearchContext {
    final NameSearchFilter filter;
    final NameSort sort;
    final PageArgs page;
    private final Supplier<CountResult> count;
    private final Supplier<Boolean> candidatesCapped;

    NameSearchContext(NameSearchFilter filter, NameSort sort, PageArgs page) {
      this.filter = filter;
      this.sort = sort;
      this.page = page;
      this.count = memoize(() -> names.count(filter));
      this.candidatesCapped = memoize(() -> names.titleCandidatesCapped(filter));
    }

    CountResult count() {
      return count.get();
    }
  }

  @DgsQuery
  public TitleSearchContext searchTitles(
      @InputArgument TitleSearchFilter filter, @InputArgument TitleSort sort,
      @InputArgument Integer limit, @InputArgument Integer offset) {
    TitleSearchFilter f = filter == null ? TitleSearchFilter.empty() : filter;
    TitleSort s = sort == null ? TitleSort.RELEVANCE : sort;
    validation.validate(f, s, limit, offset);
    return new TitleSearchContext(f, s, PageArgs.clamp(limit, offset, props.defaultLimit(), props.maxLimit()));
  }

  @DgsQuery
  public NameSearchContext searchNames(
      @InputArgument NameSearchFilter filter, @InputArgument NameSort sort,
      @InputArgument Integer limit, @InputArgument Integer offset) {
    NameSearchFilter f = filter == null ? NameSearchFilter.empty() : filter;
    NameSort s = sort == null ? NameSort.RELEVANCE : sort;
    validation.validate(f, s, limit, offset);
    return new NameSearchContext(f, s, PageArgs.clamp(limit, offset, props.defaultLimit(), props.maxLimit()));
  }

  @DgsQuery
  public CompletableFuture<List<Object>> search(
      @InputArgument String query, @InputArgument List<SearchKind> kinds,
      @InputArgument Integer limit) {
    if (query == null || query.strip().length() < 2) {
      throw new com.netflix.graphql.dgs.exceptions.DgsBadRequestException(
          "query must be at least 2 characters");
    }
    java.util.Set<SearchKind> kindSet = kinds == null || kinds.isEmpty()
        ? java.util.EnumSet.allOf(SearchKind.class)
        : java.util.EnumSet.copyOf(kinds);
    int n = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
    return CompletableFuture.supplyAsync(() -> unified.search(query.strip(), kindSet, n), executor);
  }

  @DgsQuery
  public SearchInfo searchInfo() {
    Document meta = mongo.findById("rebuild", Document.class, "search_meta");
    if (meta == null) {
      return new SearchInfo(null, null, null);
    }
    java.util.Date rebuiltAt = meta.get("rebuiltAt", java.util.Date.class);
    return new SearchInfo(
        rebuiltAt == null ? null : rebuiltAt.toInstant().toString(),
        meta.getInteger("titleCount"),
        meta.getInteger("nameCount"));
  }

  @DgsData(parentType = "TitleSearchResult", field = "items")
  public CompletableFuture<List<Title>> titleItems(DgsDataFetchingEnvironment dfe) {
    TitleSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> titles.items(ctx.filter, ctx.sort, ctx.page), executor);
  }

  @DgsData(parentType = "TitleSearchResult", field = "total")
  public CompletableFuture<Integer> titleTotal(DgsDataFetchingEnvironment dfe) {
    TitleSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> ctx.count().total(), executor);
  }

  @DgsData(parentType = "TitleSearchResult", field = "totalIsCapped")
  public CompletableFuture<Boolean> titleTotalCapped(DgsDataFetchingEnvironment dfe) {
    TitleSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> ctx.count().capped(), executor);
  }

  @DgsData(parentType = "NameSearchResult", field = "items")
  public CompletableFuture<List<Name>> nameItems(DgsDataFetchingEnvironment dfe) {
    NameSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> names.items(ctx.filter, ctx.sort, ctx.page), executor);
  }

  @DgsData(parentType = "NameSearchResult", field = "total")
  public CompletableFuture<Integer> nameTotal(DgsDataFetchingEnvironment dfe) {
    NameSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> ctx.count().total(), executor);
  }

  @DgsData(parentType = "NameSearchResult", field = "totalIsCapped")
  public CompletableFuture<Boolean> nameTotalCapped(DgsDataFetchingEnvironment dfe) {
    NameSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(() -> ctx.count().capped(), executor);
  }

  @DgsData(parentType = "NameSearchResult", field = "titleCandidatesCapped")
  public CompletableFuture<Boolean> nameCandidatesCapped(DgsDataFetchingEnvironment dfe) {
    NameSearchContext ctx = dfe.getSource();
    return CompletableFuture.supplyAsync(ctx.candidatesCapped::get, executor);
  }

  /** Thread-safe single-computation memo so total + totalIsCapped share one count query. */
  private static <T> Supplier<T> memoize(Supplier<T> delegate) {
    return new Supplier<>() {
      private volatile T value;

      @Override
      public T get() {
        T v = value;
        if (v == null) {
          synchronized (this) {
            v = value;
            if (v == null) {
              v = delegate.get();
              value = v;
            }
          }
        }
        return v;
      }
    };
  }
}

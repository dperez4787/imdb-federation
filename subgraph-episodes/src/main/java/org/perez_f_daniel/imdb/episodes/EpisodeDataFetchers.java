package org.perez_f_daniel.imdb.episodes;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.perez_f_daniel.imdb.common.GroupPage;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@DgsComponent
public class EpisodeDataFetchers {

  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 500;

  private final MongoTemplate mongo;

  public EpisodeDataFetchers(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @DgsEntityFetcher(name = "Title")
  public Title titleEntity(Map<String, Object> values) {
    return new Title((String) values.get("tconst"));
  }

  @DgsData(parentType = "Title", field = "episode")
  public CompletableFuture<Episode> episode(DgsDataFetchingEnvironment dfe) {
    Title source = dfe.getSource();
    return dfe.<String, Episode>getDataLoader("episodeByTconst").load(source.tconst());
  }

  @DgsData(parentType = "Title", field = "episodes")
  public CompletableFuture<List<Title>> episodes(
      @InputArgument Integer limit, @InputArgument Integer offset, DgsDataFetchingEnvironment dfe) {
    Title source = dfe.getSource();
    PageArgs page = PageArgs.clamp(limit, offset, DEFAULT_LIMIT, MAX_LIMIT);
    return dfe.<GroupPage, List<Title>>getDataLoader("episodesByParent")
        .load(new GroupPage(source.tconst(), page.limit(), page.offset()))
        .thenApply(list -> list == null ? List.of() : list);
  }

  /** Single-parent root lookup: plain indexed find, no loader needed. */
  @DgsQuery
  public List<Title> episodesOfSeries(
      @InputArgument String parentTconst, @InputArgument Integer limit, @InputArgument Integer offset) {
    PageArgs page = PageArgs.clamp(limit, offset, DEFAULT_LIMIT, MAX_LIMIT);
    Query query = Query.query(Criteria.where("parentTconst").is(parentTconst))
        .with(EpisodeLoaders.EPISODE_ORDER)
        .skip(page.offset())
        .limit(page.limit());
    return mongo.find(query, EpisodeDoc.class).stream()
        .map(doc -> new Title(doc.tconst()))
        .toList();
  }
}

package org.perez_f_daniel.imdb.titles;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class TitleDataFetchers {

  static final int MAX_BATCH_IDS = 100;

  @DgsQuery
  public CompletableFuture<Title> title(@InputArgument String tconst, DataFetchingEnvironment dfe) {
    return dfe.<String, Title>getDataLoader("titlesByTconst").load(tconst);
  }

  @DgsQuery
  public CompletableFuture<List<Title>> titles(
      @InputArgument List<String> tconsts, DataFetchingEnvironment dfe) {
    if (tconsts.size() > MAX_BATCH_IDS) {
      throw new IllegalArgumentException("titles accepts at most " + MAX_BATCH_IDS + " ids");
    }
    return dfe.<String, Title>getDataLoader("titlesByTconst").loadMany(tconsts);
  }

  @DgsEntityFetcher(name = "Title")
  public CompletableFuture<Title> titleEntity(
      Map<String, Object> values, DgsDataFetchingEnvironment dfe) {
    return dfe.<String, Title>getDataLoader("titlesByTconst").load((String) values.get("tconst"));
  }
}

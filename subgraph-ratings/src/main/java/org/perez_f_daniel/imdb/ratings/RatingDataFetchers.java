package org.perez_f_daniel.imdb.ratings;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class RatingDataFetchers {

  /** Contributor pattern: zero-cost key stub; the DB read happens at field level. */
  @DgsEntityFetcher(name = "Title")
  public Title titleEntity(Map<String, Object> values) {
    return new Title((String) values.get("tconst"));
  }

  @DgsData(parentType = "Title", field = "rating")
  public CompletableFuture<Rating> rating(DgsDataFetchingEnvironment dfe) {
    Title source = dfe.getSource();
    return dfe.<String, Rating>getDataLoader("ratingsByTconst").load(source.tconst());
  }
}

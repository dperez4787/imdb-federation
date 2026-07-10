package org.perez_f_daniel.imdb.akas;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@DgsComponent
public class AkaDataFetchers {

  @DgsEntityFetcher(name = "Title")
  public Title titleEntity(Map<String, Object> values) {
    return new Title((String) values.get("tconst"));
  }

  @DgsData(parentType = "Title", field = "akas")
  public CompletableFuture<List<TitleAka>> akas(DgsDataFetchingEnvironment dfe) {
    Title source = dfe.getSource();
    return dfe.<String, List<TitleAka>>getDataLoader("akasByTitleId")
        .load(source.tconst())
        .thenApply(list -> list == null ? List.of() : list);
  }
}

package org.perez_f_daniel.imdb.names;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.perez_f_daniel.imdb.common.CsvValues;

@DgsComponent
public class NameDataFetchers {

  static final int MAX_BATCH_IDS = 100;

  @DgsQuery
  public CompletableFuture<Name> name(@InputArgument String nconst, DataFetchingEnvironment dfe) {
    return dfe.<String, Name>getDataLoader("namesByNconst").load(nconst);
  }

  @DgsQuery
  public CompletableFuture<List<Name>> names(
      @InputArgument List<String> nconsts, DataFetchingEnvironment dfe) {
    if (nconsts.size() > MAX_BATCH_IDS) {
      throw new IllegalArgumentException("names accepts at most " + MAX_BATCH_IDS + " ids");
    }
    return dfe.<String, Name>getDataLoader("namesByNconst").loadMany(nconsts);
  }

  @DgsEntityFetcher(name = "Name")
  public CompletableFuture<Name> nameEntity(
      Map<String, Object> values, DgsDataFetchingEnvironment dfe) {
    return dfe.<String, Name>getDataLoader("namesByNconst").load((String) values.get("nconst"));
  }

  /** csv of tconsts -> Title key stubs; the router hydrates them from other subgraphs. */
  @DgsData(parentType = "Name", field = "knownForTitles")
  public List<Title> knownForTitles(DgsDataFetchingEnvironment dfe) {
    Name source = dfe.getSource();
    List<String> tconsts = CsvValues.split(source.knownForTitlesCsv());
    return tconsts == null ? null : tconsts.stream().map(Title::new).toList();
  }
}

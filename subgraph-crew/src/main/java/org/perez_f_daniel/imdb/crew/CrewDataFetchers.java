package org.perez_f_daniel.imdb.crew;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.perez_f_daniel.imdb.common.CsvValues;

@DgsComponent
public class CrewDataFetchers {

  @DgsEntityFetcher(name = "Title")
  public Title titleEntity(Map<String, Object> values) {
    return new Title((String) values.get("tconst"));
  }

  @DgsData(parentType = "Title", field = "directors")
  public CompletableFuture<List<Name>> directors(DgsDataFetchingEnvironment dfe) {
    return crewField(dfe, CrewDoc::directors);
  }

  @DgsData(parentType = "Title", field = "writers")
  public CompletableFuture<List<Name>> writers(DgsDataFetchingEnvironment dfe) {
    return crewField(dfe, CrewDoc::writers);
  }

  /** Both fields share one loader key, so directors + writers still cost one find. */
  private CompletableFuture<List<Name>> crewField(
      DgsDataFetchingEnvironment dfe, Function<CrewDoc, String> column) {
    Title source = dfe.getSource();
    return dfe.<String, CrewDoc>getDataLoader("crewByTconst")
        .load(source.tconst())
        .thenApply(doc -> {
          if (doc == null) {
            return null;
          }
          List<String> nconsts = CsvValues.split(column.apply(doc));
          return nconsts == null ? null : nconsts.stream().map(Name::new).toList();
        });
  }
}

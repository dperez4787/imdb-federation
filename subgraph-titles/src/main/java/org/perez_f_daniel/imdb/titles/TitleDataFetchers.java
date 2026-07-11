package org.perez_f_daniel.imdb.titles;

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
import org.springframework.beans.factory.annotation.Value;

@DgsComponent
public class TitleDataFetchers {

  static final int MAX_BATCH_IDS = 100;

  private final String omdbApiKey;

  public TitleDataFetchers(@Value("${omdb.api-key:}") String omdbApiKey) {
    this.omdbApiKey = omdbApiKey == null ? "" : omdbApiKey.strip();
  }

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

  /**
   * OMDb poster URL with the API key attached server-side, so the key lives in
   * Secret Manager instead of client bundles and is served only to authenticated
   * callers. Access-gating, not secrecy: the returned URL still embeds the key.
   * Computed field (no doc column), so a child fetcher rather than a mapper field;
   * covers both root queries and entity hydration — same Title source object.
   */
  @DgsData(parentType = "Title", field = "imgUrl")
  public String imgUrl(DgsDataFetchingEnvironment dfe) {
    Title title = dfe.getSource();
    if (omdbApiKey.isBlank() || title.tconst() == null) {
      return null;
    }
    return "https://img.omdbapi.com/?i=" + title.tconst() + "&apikey=" + omdbApiKey;
  }
}

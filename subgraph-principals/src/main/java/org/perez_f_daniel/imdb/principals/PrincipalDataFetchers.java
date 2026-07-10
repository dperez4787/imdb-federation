package org.perez_f_daniel.imdb.principals;

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
public class PrincipalDataFetchers {

  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 200;

  private final MongoTemplate mongo;

  public PrincipalDataFetchers(MongoTemplate mongo) {
    this.mongo = mongo;
  }

  @DgsEntityFetcher(name = "Title")
  public Title titleEntity(Map<String, Object> values) {
    return new Title((String) values.get("tconst"));
  }

  @DgsEntityFetcher(name = "Name")
  public Name nameEntity(Map<String, Object> values) {
    return new Name((String) values.get("nconst"));
  }

  @DgsData(parentType = "Title", field = "principals")
  public CompletableFuture<List<Principal>> principals(DgsDataFetchingEnvironment dfe) {
    Title source = dfe.getSource();
    return dfe.<String, List<Principal>>getDataLoader("principalsByTconst")
        .load(source.tconst())
        .thenApply(list -> list == null ? List.of() : list);
  }

  @DgsData(parentType = "Name", field = "credits")
  public CompletableFuture<List<Principal>> credits(
      @InputArgument Integer limit, @InputArgument Integer offset, DgsDataFetchingEnvironment dfe) {
    Name source = dfe.getSource();
    PageArgs page = PageArgs.clamp(limit, offset, DEFAULT_LIMIT, MAX_LIMIT);
    return dfe.<GroupPage, List<Principal>>getDataLoader("creditsByNconst")
        .load(new GroupPage(source.nconst(), page.limit(), page.offset()))
        .thenApply(list -> list == null ? List.of() : list);
  }

  /** Single-person root lookup: plain indexed find, no loader needed. */
  @DgsQuery
  public List<Principal> principalsByName(
      @InputArgument String nconst, @InputArgument Integer limit, @InputArgument Integer offset) {
    PageArgs page = PageArgs.clamp(limit, offset, DEFAULT_LIMIT, MAX_LIMIT);
    Query query = Query.query(Criteria.where("nconst").is(nconst))
        .with(PrincipalLoaders.CREDITS_ORDER)
        .skip(page.offset())
        .limit(page.limit());
    return mongo.find(query, PrincipalDoc.class).stream()
        .map(PrincipalMapper::toGraphql)
        .toList();
  }
}

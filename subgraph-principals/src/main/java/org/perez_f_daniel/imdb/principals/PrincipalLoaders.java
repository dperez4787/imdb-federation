package org.perez_f_daniel.imdb.principals;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.perez_f_daniel.imdb.common.GroupPage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

public class PrincipalLoaders {

  static final Sort CREDITS_ORDER = Sort.by("tconst", "ordering");

  private PrincipalLoaders() {}

  @DgsDataLoader(name = "principalsByTconst")
  public static class PrincipalsByTconst implements MappedBatchLoader<String, List<Principal>> {
    private final MappedBatchLoader<String, List<Principal>> delegate;

    public PrincipalsByTconst(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
      this.delegate = BatchLoaders.groupedByKey(mongo, executor, PrincipalDoc.class, "tconst",
          Sort.by("ordering"), PrincipalDoc::tconst, PrincipalMapper::toGraphql);
    }

    @Override
    public CompletionStage<Map<String, List<Principal>>> load(Set<String> keys) {
      return delegate.load(keys);
    }
  }

  @DgsDataLoader(name = "creditsByNconst")
  public static class CreditsByNconst implements MappedBatchLoader<GroupPage, List<Principal>> {
    private final MappedBatchLoader<GroupPage, List<Principal>> delegate;

    public CreditsByNconst(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
      this.delegate = BatchLoaders.groupedPaged(mongo, executor, PrincipalDoc.class,
          "title_principals", "nconst", CREDITS_ORDER, PrincipalMapper::toGraphql);
    }

    @Override
    public CompletionStage<Map<GroupPage, List<Principal>>> load(Set<GroupPage> keys) {
      return delegate.load(keys);
    }
  }
}

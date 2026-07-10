package org.perez_f_daniel.imdb.names;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsDataLoader(name = "namesByNconst")
public class NamesByNconstLoader implements MappedBatchLoader<String, Name> {

  private final MappedBatchLoader<String, Name> delegate;

  public NamesByNconstLoader(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.delegate = BatchLoaders.byUniqueKey(
        mongo, executor, NameDoc.class, "nconst", NameDoc::nconst, NameMapper::toGraphql);
  }

  @Override
  public CompletionStage<Map<String, Name>> load(Set<String> keys) {
    return delegate.load(keys);
  }
}

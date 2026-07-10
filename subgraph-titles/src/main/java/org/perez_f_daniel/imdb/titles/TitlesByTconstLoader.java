package org.perez_f_daniel.imdb.titles;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsDataLoader(name = "titlesByTconst")
public class TitlesByTconstLoader implements MappedBatchLoader<String, Title> {

  private final MappedBatchLoader<String, Title> delegate;

  public TitlesByTconstLoader(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.delegate = BatchLoaders.byUniqueKey(
        mongo, executor, TitleDoc.class, "tconst", TitleDoc::tconst, TitleMapper::toGraphql);
  }

  @Override
  public CompletionStage<Map<String, Title>> load(Set<String> keys) {
    return delegate.load(keys);
  }
}

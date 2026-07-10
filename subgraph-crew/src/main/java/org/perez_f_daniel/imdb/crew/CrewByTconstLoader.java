package org.perez_f_daniel.imdb.crew;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsDataLoader(name = "crewByTconst")
public class CrewByTconstLoader implements MappedBatchLoader<String, CrewDoc> {

  private final MappedBatchLoader<String, CrewDoc> delegate;

  public CrewByTconstLoader(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.delegate = BatchLoaders.byUniqueKey(
        mongo, executor, CrewDoc.class, "tconst", CrewDoc::tconst, doc -> doc);
  }

  @Override
  public CompletionStage<Map<String, CrewDoc>> load(Set<String> keys) {
    return delegate.load(keys);
  }
}

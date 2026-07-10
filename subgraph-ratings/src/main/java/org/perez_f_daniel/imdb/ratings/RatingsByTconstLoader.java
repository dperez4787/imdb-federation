package org.perez_f_daniel.imdb.ratings;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.perez_f_daniel.imdb.common.Fields;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsDataLoader(name = "ratingsByTconst")
public class RatingsByTconstLoader implements MappedBatchLoader<String, Rating> {

  private final MappedBatchLoader<String, Rating> delegate;

  public RatingsByTconstLoader(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.delegate = BatchLoaders.byUniqueKey(mongo, executor, RatingDoc.class, "tconst",
        RatingDoc::tconst, doc -> new Rating(doc.averageRating(), Fields.toInt(doc.numVotes())));
  }

  @Override
  public CompletionStage<Map<String, Rating>> load(Set<String> keys) {
    return delegate.load(keys);
  }
}

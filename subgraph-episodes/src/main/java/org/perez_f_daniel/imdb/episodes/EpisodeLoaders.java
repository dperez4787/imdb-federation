package org.perez_f_daniel.imdb.episodes;

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

public class EpisodeLoaders {

  static final Sort EPISODE_ORDER = Sort.by("seasonNumber", "episodeNumber");

  private EpisodeLoaders() {}

  @DgsDataLoader(name = "episodeByTconst")
  public static class EpisodeByTconst implements MappedBatchLoader<String, Episode> {
    private final MappedBatchLoader<String, Episode> delegate;

    public EpisodeByTconst(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
      this.delegate = BatchLoaders.byUniqueKey(mongo, executor, EpisodeDoc.class, "tconst",
          EpisodeDoc::tconst,
          doc -> new Episode(doc.seasonNumber(), doc.episodeNumber(), new Title(doc.parentTconst())));
    }

    @Override
    public CompletionStage<Map<String, Episode>> load(Set<String> keys) {
      return delegate.load(keys);
    }
  }

  @DgsDataLoader(name = "episodesByParent")
  public static class EpisodesByParent implements MappedBatchLoader<GroupPage, List<Title>> {
    private final MappedBatchLoader<GroupPage, List<Title>> delegate;

    public EpisodesByParent(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
      this.delegate = BatchLoaders.groupedPaged(mongo, executor, EpisodeDoc.class, "title_episode",
          "parentTconst", EPISODE_ORDER, doc -> new Title(doc.tconst()));
    }

    @Override
    public CompletionStage<Map<GroupPage, List<Title>>> load(Set<GroupPage> keys) {
      return delegate.load(keys);
    }
  }
}

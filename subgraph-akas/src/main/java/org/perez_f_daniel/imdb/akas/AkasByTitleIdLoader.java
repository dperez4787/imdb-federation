package org.perez_f_daniel.imdb.akas;

import com.netflix.graphql.dgs.DgsDataLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.dataloader.MappedBatchLoader;
import org.perez_f_daniel.imdb.common.BatchLoaders;
import org.perez_f_daniel.imdb.common.CsvValues;
import org.perez_f_daniel.imdb.common.Fields;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

@DgsDataLoader(name = "akasByTitleId")
public class AkasByTitleIdLoader implements MappedBatchLoader<String, List<TitleAka>> {

  private final MappedBatchLoader<String, List<TitleAka>> delegate;

  public AkasByTitleIdLoader(MongoTemplate mongo, @Qualifier("imdbLoaderExecutor") Executor executor) {
    this.delegate = BatchLoaders.groupedByKey(
        mongo, executor, AkaDoc.class, "titleId", Sort.by("ordering"),
        AkaDoc::titleId, AkasByTitleIdLoader::toGraphql);
  }

  static TitleAka toGraphql(AkaDoc doc) {
    return new TitleAka(
        doc.ordering(),
        doc.title(),
        doc.region(),
        doc.language(),
        CsvValues.split(doc.types()),
        CsvValues.split(doc.attributes()),
        Fields.toBoolean(doc.isOriginalTitle()));
  }

  @Override
  public CompletionStage<Map<String, List<TitleAka>>> load(Set<String> keys) {
    return delegate.load(keys);
  }
}

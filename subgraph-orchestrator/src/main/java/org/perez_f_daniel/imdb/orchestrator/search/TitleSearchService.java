package org.perez_f_daniel.imdb.orchestrator.search;

import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.Title;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class TitleSearchService {

  private final MongoTemplate mongo;
  private final TitleTypesCache titleTypes;
  private final SearchProperties props;

  public TitleSearchService(MongoTemplate mongo, TitleTypesCache titleTypes, SearchProperties props) {
    this.mongo = mongo;
    this.titleTypes = titleTypes;
    this.props = props;
  }

  public List<Title> items(TitleSearchFilter filter, TitleSort sort, PageArgs page) {
    List<Document> pipeline = TitlePipelines.items(filter, sort, page, titleTypes.get(), props);
    List<Title> out = new ArrayList<>(page.limit());
    for (Document d : run(TitlePipelines.collectionFor(TitlePipelines.strategyFor(filter)), pipeline)) {
      out.add(new Title(d.getString("tconst")));
    }
    return out;
  }

  public CountResult count(TitleSearchFilter filter, TitleSort sort) {
    List<Document> pipeline = TitlePipelines.count(filter, sort, titleTypes.get(), props);
    Document first = run(TitlePipelines.collectionFor(TitlePipelines.strategyFor(filter)), pipeline)
        .first();
    int n = first == null ? 0 : first.getInteger("n", 0);
    return CountResult.of(n, props.countCap());
  }

  private com.mongodb.client.AggregateIterable<Document> run(String collection, List<Document> pipeline) {
    MongoCollection<Document> coll = mongo.getCollection(collection);
    return coll.aggregate(pipeline).allowDiskUse(true);
  }
}

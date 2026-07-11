package org.perez_f_daniel.imdb.orchestrator.search;

import com.mongodb.client.AggregateIterable;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.Name;
import org.perez_f_daniel.imdb.orchestrator.api.NameSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.NameSort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class NameSearchService {

  private final MongoTemplate mongo;
  private final SearchProperties props;

  public NameSearchService(MongoTemplate mongo, SearchProperties props) {
    this.mongo = mongo;
    this.props = props;
  }

  public List<Name> items(NameSearchFilter filter, NameSort sort, PageArgs page) {
    List<Document> pipeline =
        NamePipelines.items(filter, sort, page, resolvedInTitles(filter), props);
    List<Name> out = new ArrayList<>(page.limit());
    for (Document d : run(filter, pipeline)) {
      out.add(new Name(d.getString("nconst")));
    }
    return out;
  }

  public CountResult count(NameSearchFilter filter) {
    List<Document> pipeline = NamePipelines.count(filter, resolvedInTitles(filter), props);
    Document first = run(filter, pipeline).first();
    int n = first == null ? 0 : first.getInteger("n", 0);
    return CountResult.of(n, props.countCap());
  }

  /** Whether the open-ended title scope exceeded the candidate cap (TITLES_FIRST only). */
  public boolean titleCandidatesCapped(NameSearchFilter filter) {
    if (NamePipelines.strategyFor(filter) != NamePipelines.Strategy.TITLES_FIRST) {
      return false;
    }
    Document first = mongo.getCollection("search_titles")
        .aggregate(NamePipelines.titleScopeCount(filter, props)).allowDiskUse(true)
        .maxTime(props.queryTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .first();
    int n = first == null ? 0 : first.getInteger("n", 0);
    return n > props.titleCandidateCap();
  }

  /**
   * inTitles combined with inGenres/activeFrom/To: pre-filter the explicit tconst
   * list through search_titles so the principals-first pipeline sees only titles
   * matching the whole scope. Public: facet fetchers need the same resolution.
   */
  public List<String> resolvedInTitles(NameSearchFilter f) {
    if (!f.hasInTitles()) {
      return List.of();
    }
    Document scope = NamePipelines.titleScopeMatch(f);
    if (scope.isEmpty()) {
      return f.inTitles();
    }
    Query q = Query.query(Criteria.where("_id").in(f.inTitles()));
    scope.forEach((k, v) -> q.addCriteria(Criteria.where(k).is(v)));
    return mongo.find(q, Document.class, "search_titles").stream()
        .map(d -> d.getString("_id"))
        .toList();
  }

  private AggregateIterable<Document> run(NameSearchFilter filter, List<Document> pipeline) {
    String collection = NamePipelines.collectionFor(NamePipelines.strategyFor(filter));
    AggregateIterable<Document> it = mongo.getCollection(collection).aggregate(pipeline)
        .allowDiskUse(true)
        .maxTime(props.queryTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
    NamePipelines.hintFor(filter).ifPresent(it::hintString);
    return it;
  }
}

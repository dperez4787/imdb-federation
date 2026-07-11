package org.perez_f_daniel.imdb.orchestrator.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.perez_f_daniel.imdb.orchestrator.api.FacetBucket;
import org.perez_f_daniel.imdb.orchestrator.api.FacetValue;
import org.perez_f_daniel.imdb.orchestrator.api.Facets;
import org.perez_f_daniel.imdb.orchestrator.api.NameFacetDimension;
import org.perez_f_daniel.imdb.orchestrator.api.NameSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.TitleFacetDimension;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSearchFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Two facet flavors: vocabulary facets read the search_facets collection
 * (materialized by the rebuild), contextual facets run one $facet aggregation
 * over the search's own base stages, capped at the same 10k candidate budget
 * as counts. Typed dimensions instead of raw pipelines — the aggregation
 * flexibility without the injection/DoS surface.
 */
@Service
public class FacetService {

  private final MongoTemplate mongo;
  private final TitleTypesCache titleTypes;
  private final SearchProperties props;

  public FacetService(MongoTemplate mongo, TitleTypesCache titleTypes, SearchProperties props) {
    this.mongo = mongo;
    this.titleTypes = titleTypes;
    this.props = props;
  }

  public Facets vocabulary() {
    Map<String, List<FacetValue>> byId = new java.util.HashMap<>();
    for (Document d : mongo.getCollection("search_facets").find()) {
      byId.put(d.getString("_id"), toValues(d.getList("values", Document.class)));
    }
    return new Facets(
        byId.getOrDefault("genres", List.of()),
        byId.getOrDefault("titleTypes", List.of()),
        byId.getOrDefault("principalCategories", List.of()),
        byId.getOrDefault("professions", List.of()),
        byId.getOrDefault("akaRegions", List.of()),
        byId.getOrDefault("akaLanguages", List.of()));
  }

  public List<FacetBucket> titleFacets(
      TitleSearchFilter filter, List<TitleFacetDimension> dimensions, int perDimension) {
    List<Document> pipeline = TitlePipelines.baseStages(filter, titleTypes.get(), props);
    pipeline.add(new Document("$limit", props.countCap()));
    Document facetSpec = new Document();
    for (TitleFacetDimension dim : dimensions) {
      facetSpec.append(dim.name(), titleDimension(dim, perDimension));
    }
    pipeline.add(new Document("$facet", facetSpec));
    String collection = TitlePipelines.collectionFor(TitlePipelines.strategyFor(filter));
    return runFacets(collection, pipeline, dimensions.stream().map(Enum::name).toList());
  }

  public List<FacetBucket> nameFacets(NameSearchFilter filter, List<String> resolvedInTitles,
      List<NameFacetDimension> dimensions, int perDimension) {
    List<Document> pipeline = NamePipelines.baseStages(filter, resolvedInTitles, props);
    pipeline.add(new Document("$limit", props.countCap()));
    // scoped strategies carry the search_names doc under "n"
    String pfx = NamePipelines.strategyFor(filter) == NamePipelines.Strategy.UNSCOPED ? "" : "n.";
    Document facetSpec = new Document();
    for (NameFacetDimension dim : dimensions) {
      facetSpec.append(dim.name(), nameDimension(dim, pfx, perDimension));
    }
    pipeline.add(new Document("$facet", facetSpec));
    String collection = NamePipelines.collectionFor(NamePipelines.strategyFor(filter));
    return runFacets(collection, pipeline, dimensions.stream().map(Enum::name).toList());
  }

  private List<FacetBucket> runFacets(String collection, List<Document> pipeline, List<String> dims) {
    Document result = mongo.getCollection(collection)
        .aggregate(pipeline).allowDiskUse(true)
        .maxTime(props.queryTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .first();
    List<FacetBucket> buckets = new ArrayList<>(dims.size());
    for (String dim : dims) {
      List<Document> values = result == null ? List.of() : result.getList(dim, Document.class);
      buckets.add(new FacetBucket(dim, toValues(values)));
    }
    return buckets;
  }

  private static List<FacetValue> toValues(List<Document> docs) {
    if (docs == null) {
      return List.of();
    }
    return docs.stream()
        .map(d -> new FacetValue(d.getString("value"), d.get("count", Number.class).intValue()))
        .toList();
  }

  private static List<Document> titleDimension(TitleFacetDimension dim, int limit) {
    return switch (dim) {
      case GENRES -> valueCounts(new Document("$unwind", "$genres"), "$genres", limit, false);
      case TITLE_TYPES -> valueCounts(null, "$titleType", limit, false);
      case DECADES -> bandCounts("startYear", decadeExpr("$startYear"), limit);
      case RATING_BANDS -> bandCounts("averageRating",
          new Document("$toInt", new Document("$floor", "$averageRating")), limit);
      case RUNTIME_BANDS -> bandCounts("runtimeMinutes",
          new Document("$min", List.of(180, new Document("$toInt", new Document("$multiply", List.of(
              new Document("$floor", new Document("$divide", List.of("$runtimeMinutes", 30))),
              30))))),
          limit);
    };
  }

  private static List<Document> nameDimension(NameFacetDimension dim, String pfx, int limit) {
    return switch (dim) {
      case PROFESSIONS ->
          valueCounts(new Document("$unwind", "$" + pfx + "professions"), "$" + pfx + "professions",
              limit, false);
      case BIRTH_DECADES -> bandCounts(pfx + "birthYear", decadeExpr("$" + pfx + "birthYear"), limit);
    };
  }

  private static Document decadeExpr(String field) {
    return new Document("$toInt", new Document("$multiply", List.of(
        new Document("$floor", new Document("$divide", List.of(field, 10))), 10)));
  }

  /** value-count sub-pipeline: [unwind?] -> group -> sort count desc -> limit -> project. */
  private static List<Document> valueCounts(Document unwind, String groupExpr, int limit, boolean numeric) {
    List<Document> p = new ArrayList<>();
    if (unwind != null) {
      p.add(unwind);
    }
    p.add(new Document("$group",
        new Document("_id", groupExpr).append("count", new Document("$sum", 1))));
    p.add(new Document("$sort", new Document("count", -1).append("_id", 1)));
    p.add(new Document("$limit", limit));
    p.add(new Document("$project", new Document("_id", 0)
        .append("value", numeric ? new Document("$toString", "$_id") : "$_id")
        .append("count", 1)));
    return p;
  }

  /** numeric band sub-pipeline: match present -> group by band expr -> sort band desc. */
  private static List<Document> bandCounts(String presenceField, Document bandExpr, int limit) {
    List<Document> p = new ArrayList<>();
    p.add(new Document("$match", new Document(presenceField, new Document("$exists", true))));
    p.add(new Document("$group",
        new Document("_id", bandExpr).append("count", new Document("$sum", 1))));
    p.add(new Document("$sort", new Document("_id", -1)));
    p.add(new Document("$limit", limit));
    p.add(new Document("$project", new Document("_id", 0)
        .append("value", new Document("$toString", "$_id"))
        .append("count", 1)));
    return p;
  }
}

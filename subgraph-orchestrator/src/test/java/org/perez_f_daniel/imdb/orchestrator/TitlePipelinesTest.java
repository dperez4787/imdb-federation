package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.PeopleMatchMode;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.TitleSort;
import org.perez_f_daniel.imdb.orchestrator.search.SearchProperties;
import org.perez_f_daniel.imdb.orchestrator.search.TitlePipelines;
import org.perez_f_daniel.imdb.orchestrator.search.TitlePipelines.Strategy;

class TitlePipelinesTest {

  private static final SearchProperties PROPS =
      new SearchProperties(null, null, null, null, null, null, null, null);
  private static final PageArgs PAGE = new PageArgs(25, 0);
  private static final List<String> ALL_TYPES = List.of("movie", "short", "tvSeries");

  private static TitleSearchFilter withPeople(List<String> people, PeopleMatchMode mode) {
    return new TitleSearchFilter(null, null, null, null, null, null, null, null, null,
        null, null, null, false, people, mode, null);
  }

  private static TitleSearchFilter withQuery(String q) {
    return new TitleSearchFilter(q, null, null, null, null, null, null, null, null,
        null, null, null, false, null, null, null);
  }

  @Test
  void strategySelection() {
    assertThat(TitlePipelines.strategyFor(TitleSearchFilter.empty())).isEqualTo(Strategy.PLAIN);
    assertThat(TitlePipelines.strategyFor(withPeople(List.of("nm1"), null)))
        .isEqualTo(Strategy.PEOPLE_FIRST);
    TitleSearchFilter both = new TitleSearchFilter("dune", null, null, null, null, null, null,
        null, null, null, null, null, false, List.of("nm1"), null, null);
    assertThat(TitlePipelines.strategyFor(both)).isEqualTo(Strategy.TEXT_FIRST);
    assertThat(TitlePipelines.collectionFor(Strategy.PEOPLE_FIRST)).isEqualTo("title_principals");
    assertThat(TitlePipelines.collectionFor(Strategy.PLAIN)).isEqualTo("search_titles");
  }

  @Test
  void absentTitleTypesExpandToKnownTypes() {
    List<Document> p = TitlePipelines.items(
        TitleSearchFilter.empty(), TitleSort.POPULARITY_DESC, PAGE, ALL_TYPES, PROPS);
    Document match = p.get(0).get("$match", Document.class);
    assertThat(match.get("titleType", Document.class).getList("$in", String.class))
        .isEqualTo(ALL_TYPES);
  }

  @Test
  void noTypeExpansionBeforeFirstRebuild() {
    List<Document> p = TitlePipelines.items(
        TitleSearchFilter.empty(), TitleSort.POPULARITY_DESC, PAGE, List.of(), PROPS);
    assertThat(p.get(0).get("$match", Document.class)).doesNotContainKey("titleType");
  }

  @Test
  void everySortEndsWithIdTiebreak() {
    for (TitleSort sort : TitleSort.values()) {
      List<Document> p = TitlePipelines.items(
          TitleSearchFilter.empty(), sort, PAGE, ALL_TYPES, PROPS);
      Document sortStage = p.stream()
          .filter(d -> d.containsKey("$sort"))
          .reduce((a, b) -> b).orElseThrow()
          .get("$sort", Document.class);
      assertThat(sortStage.keySet()).last().isEqualTo("_id");
    }
  }

  @Test
  void relevanceWithoutQueryFallsBackToPopularity() {
    List<Document> p = TitlePipelines.items(
        TitleSearchFilter.empty(), TitleSort.RELEVANCE, PAGE, ALL_TYPES, PROPS);
    Document sort = p.stream().filter(d -> d.containsKey("$sort")).findFirst().orElseThrow()
        .get("$sort", Document.class);
    assertThat(sort.getInteger("numVotes")).isEqualTo(-1);
  }

  @Test
  void relevanceWithQuerySortsByScore() {
    List<Document> p = TitlePipelines.items(
        withQuery("dune"), TitleSort.RELEVANCE, PAGE, ALL_TYPES, PROPS);
    assertThat(p.get(0).get("$match", Document.class)).containsKey("$text");
    assertThat(p.get(1)).containsKey("$addFields");
    Document sort = p.stream().filter(d -> d.containsKey("$sort")).findFirst().orElseThrow()
        .get("$sort", Document.class);
    assertThat(sort.getInteger("score")).isEqualTo(-1);
  }

  @Test
  void peopleFirstAllModeChecksDistinctPeopleSize() {
    List<Document> p = TitlePipelines.items(
        withPeople(List.of("nm1", "nm2", "nm1"), PeopleMatchMode.ALL),
        TitleSort.POPULARITY_DESC, PAGE, ALL_TYPES, PROPS);
    // dedup: nm1,nm2 -> size check against 2
    Document sizeMatch = p.get(2).get("$match", Document.class);
    List<?> eq = sizeMatch.get("$expr", Document.class).getList("$eq", Object.class);
    assertThat(eq.get(1)).isEqualTo(2);
  }

  @Test
  void peopleFirstAnyModeSkipsSizeCheck() {
    List<Document> p = TitlePipelines.items(
        withPeople(List.of("nm1", "nm2"), PeopleMatchMode.ANY),
        TitleSort.POPULARITY_DESC, PAGE, ALL_TYPES, PROPS);
    assertThat(p.get(2)).containsKey("$lookup");
  }

  @Test
  void countVariantCapsAndCounts() {
    List<Document> p = TitlePipelines.count(
        TitleSearchFilter.empty(), TitleSort.RELEVANCE, ALL_TYPES, PROPS);
    assertThat(p.get(p.size() - 2).getInteger("$limit")).isEqualTo(10001);
    assertThat(p.get(p.size() - 1).getString("$count")).isEqualTo("n");
    assertThat(p).noneMatch(d -> d.containsKey("$skip"));
  }

  @Test
  void adultExcludedByDefault() {
    List<Document> p = TitlePipelines.items(
        TitleSearchFilter.empty(), TitleSort.POPULARITY_DESC, PAGE, ALL_TYPES, PROPS);
    assertThat(p.get(0).get("$match", Document.class).getBoolean("isAdult")).isFalse();
  }
}

package org.perez_f_daniel.imdb.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.perez_f_daniel.imdb.common.PageArgs;
import org.perez_f_daniel.imdb.orchestrator.api.NameSearchFilter;
import org.perez_f_daniel.imdb.orchestrator.api.NameSort;
import org.perez_f_daniel.imdb.orchestrator.search.NamePipelines;
import org.perez_f_daniel.imdb.orchestrator.search.NamePipelines.Strategy;
import org.perez_f_daniel.imdb.orchestrator.search.SearchProperties;

class NamePipelinesTest {

  private static final SearchProperties PROPS =
      new SearchProperties(null, null, null, null, null, null, null, null, null, null, null);
  private static final PageArgs PAGE = new PageArgs(25, 0);

  private static NameSearchFilter inTitles(List<String> t) {
    return new NameSearchFilter(null, null, null, null, null, t, null, null, null, null);
  }

  private static NameSearchFilter inGenres(List<String> g) {
    return new NameSearchFilter(null, null, null, null, null, null, g, null, null, null);
  }

  @Test
  void strategySelection() {
    assertThat(NamePipelines.strategyFor(NameSearchFilter.empty())).isEqualTo(Strategy.UNSCOPED);
    assertThat(NamePipelines.strategyFor(inTitles(List.of("tt1"))))
        .isEqualTo(Strategy.PRINCIPALS_FIRST);
    assertThat(NamePipelines.strategyFor(inGenres(List.of("Drama"))))
        .isEqualTo(Strategy.TITLES_FIRST);
    // inTitles wins even when combined with genres
    NameSearchFilter both = new NameSearchFilter(null, null, null, null, null,
        List.of("tt1"), List.of("Drama"), null, null, null);
    assertThat(NamePipelines.strategyFor(both)).isEqualTo(Strategy.PRINCIPALS_FIRST);
  }

  @Test
  void unscopedRelevanceFallsBackToPopularity() {
    List<Document> p = NamePipelines.items(
        NameSearchFilter.empty(), NameSort.RELEVANCE, PAGE, List.of(), PROPS);
    Document sort = p.stream().filter(d -> d.containsKey("$sort")).findFirst().orElseThrow()
        .get("$sort", Document.class);
    assertThat(sort.getInteger("popularity")).isEqualTo(-1);
    assertThat(sort.keySet()).last().isEqualTo("_id");
  }

  @Test
  void scopedRelevanceFallsBackToCredits() {
    List<Document> p = NamePipelines.items(
        inTitles(List.of("tt1")), NameSort.RELEVANCE, PAGE, List.of("tt1"), PROPS);
    Document sort = p.stream().filter(d -> d.containsKey("$sort")).findFirst().orElseThrow()
        .get("$sort", Document.class);
    assertThat(sort.getInteger("credits")).isEqualTo(-1);
  }

  @Test
  void scopedPopularitySortUsesJoinedPath() {
    List<Document> p = NamePipelines.items(
        inTitles(List.of("tt1")), NameSort.POPULARITY_DESC, PAGE, List.of("tt1"), PROPS);
    Document sort = p.stream().filter(d -> d.containsKey("$sort")).findFirst().orElseThrow()
        .get("$sort", Document.class);
    assertThat(sort.getInteger("n.popularity")).isEqualTo(-1);
  }

  @Test
  void titlesFirstCapsCandidatesDeterministically() {
    List<Document> p = NamePipelines.items(
        inGenres(List.of("Drama")), NameSort.RELEVANCE, PAGE, List.of(), PROPS);
    // $match, $sort(votes,-1/_id), $limit(cap) prefix
    assertThat(p.get(1).get("$sort", Document.class).getInteger("numVotes")).isEqualTo(-1);
    assertThat(p.get(2).getInteger("$limit")).isEqualTo(5000);
  }

  @Test
  void degradedQueryBecomesSubstringOnJoinedSet() {
    NameSearchFilter f = new NameSearchFilter("DiCaprio", null, null, null, null,
        List.of("tt1"), null, null, null, null);
    List<Document> p = NamePipelines.items(f, NameSort.RELEVANCE, PAGE, List.of("tt1"), PROPS);
    Document nameMatch = p.stream()
        .filter(d -> d.containsKey("$match")
            && d.get("$match", Document.class).containsKey("n.primaryNameLower"))
        .findFirst().orElseThrow().get("$match", Document.class);
    assertThat(nameMatch.get("n.primaryNameLower", Document.class).getString("$regex"))
        .isEqualTo("dicaprio");
    assertThat(p).noneMatch(d -> d.containsKey("$match")
        && d.get("$match", Document.class).containsKey("$text"));
  }

  @Test
  void unscopedPrefixSplitsIntoHintedCappedStages() {
    NameSearchFilter f = new NameSearchFilter(null, "dani", List.of("actor"), null, null,
        null, null, null, null, null);
    assertThat(NamePipelines.hintFor(f)).contains("name_prefix");
    List<Document> p = NamePipelines.items(f, NameSort.POPULARITY_DESC, PAGE, List.of(), PROPS);
    assertThat(p.get(0).get("$match", Document.class).keySet()).containsExactly("primaryNameLower");
    assertThat(p.get(1).getInteger("$limit")).isEqualTo(25000);
    assertThat(p.get(2).get("$match", Document.class)).doesNotContainKey("primaryNameLower");
    assertThat(p.get(2).get("$match", Document.class)).containsKey("professions");
  }

  @Test
  void scopedSearchesGetNoPrefixHint() {
    NameSearchFilter scoped = new NameSearchFilter(null, "dani", null, null, null,
        List.of("tt1"), null, null, null, null);
    assertThat(NamePipelines.hintFor(scoped)).isEmpty();
  }

  @Test
  void countVariantCounts() {
    List<Document> p = NamePipelines.count(inTitles(List.of("tt1")), List.of("tt1"), PROPS);
    assertThat(p.get(p.size() - 1).getString("$count")).isEqualTo("n");
  }

  @Test
  void titleScopeCountUsesCandidateCap() {
    List<Document> p = NamePipelines.titleScopeCount(inGenres(List.of("Drama")), PROPS);
    assertThat(p.get(1).getInteger("$limit")).isEqualTo(5001);
  }
}

package org.perez_f_daniel.imdb.orchestrator.api;

import java.util.List;

public record Facets(
    List<FacetValue> genres,
    List<FacetValue> titleTypes,
    List<FacetValue> principalCategories,
    List<FacetValue> professions,
    List<FacetValue> akaRegions,
    List<FacetValue> akaLanguages) {}

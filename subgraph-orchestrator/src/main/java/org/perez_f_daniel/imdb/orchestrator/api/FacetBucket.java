package org.perez_f_daniel.imdb.orchestrator.api;

import java.util.List;

public record FacetBucket(String dimension, List<FacetValue> values) {}

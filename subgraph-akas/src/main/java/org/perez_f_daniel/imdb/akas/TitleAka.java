package org.perez_f_daniel.imdb.akas;

import java.util.List;

public record TitleAka(
    int ordering,
    String title,
    String region,
    String language,
    List<String> types,
    List<String> attributes,
    Boolean isOriginalTitle) {}

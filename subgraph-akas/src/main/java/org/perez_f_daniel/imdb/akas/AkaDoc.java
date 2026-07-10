package org.perez_f_daniel.imdb.akas;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("title_akas")
public record AkaDoc(
    String titleId,
    Integer ordering,
    String title,
    String region,
    String language,
    String types,
    String attributes,
    Integer isOriginalTitle) {}

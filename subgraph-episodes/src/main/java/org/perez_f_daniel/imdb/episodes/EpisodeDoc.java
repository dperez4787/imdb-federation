package org.perez_f_daniel.imdb.episodes;

import org.springframework.data.mongodb.core.mapping.Document;

/** seasonNumber/episodeNumber can both be absent (real rows in the source data). */
@Document("title_episode")
public record EpisodeDoc(
    String tconst, String parentTconst, Integer seasonNumber, Integer episodeNumber) {}

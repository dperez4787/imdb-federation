package org.perez_f_daniel.imdb.ratings;

import org.springframework.data.mongodb.core.mapping.Document;

@Document("title_ratings")
public record RatingDoc(String tconst, Double averageRating, Long numVotes) {}

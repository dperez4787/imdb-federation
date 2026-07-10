package org.perez_f_daniel.imdb.crew;

import org.springframework.data.mongodb.core.mapping.Document;

/** directors/writers are csv strings of nconsts; either can be absent. */
@Document("title_crew")
public record CrewDoc(String tconst, String directors, String writers) {}

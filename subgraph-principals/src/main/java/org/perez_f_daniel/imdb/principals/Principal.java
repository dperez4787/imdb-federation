package org.perez_f_daniel.imdb.principals;

import java.util.List;

public record Principal(
    int ordering,
    String category,
    String job,
    List<String> characters,
    Name name,
    Title title) {}

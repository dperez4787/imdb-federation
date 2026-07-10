package org.perez_f_daniel.imdb.orchestrator;

import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Small source-collection fixture mirroring real pipeline document shapes
 * (csv strings, int flags, absent-when-\N). Popularity expectations:
 * nm0000002 = 2.0M + 1.9M = 3.9M, nm0000001 = 2.0M, nm0000003 = 50k, nm0000004 absent.
 */
public final class OrchestratorFixtures {

  public static final String GOT = "tt0944947";
  public static final String BB = "tt0903747";
  public static final String SHORT = "tt0000001";
  public static final String NOIR = "tt5000001";
  public static final String ADULT = "tt7000000";

  private OrchestratorFixtures() {}

  public static void seed(MongoTemplate mongo) {
    for (String c : List.of("title_basics", "title_ratings", "name_basics", "title_principals",
        "title_akas", "search_titles", "search_names", "search_meta", "search_facets",
        "search_titles_next", "search_names_next", "tmp_kft")) {
      mongo.dropCollection(c);
    }

    insert(mongo, "title_basics", """
        {"tconst":"%s","titleType":"tvSeries","primaryTitle":"Game of Thrones",
         "originalTitle":"Game of Thrones","isAdult":0,"startYear":2011,"endYear":2019,
         "runtimeMinutes":57,"genres":"Action,Adventure,Drama"}""".formatted(GOT));
    insert(mongo, "title_basics", """
        {"tconst":"%s","titleType":"tvSeries","primaryTitle":"Breaking Bad",
         "originalTitle":"Breaking Bad","isAdult":0,"startYear":2008,"endYear":2013,
         "runtimeMinutes":49,"genres":"Crime,Drama,Thriller"}""".formatted(BB));
    insert(mongo, "title_basics", """
        {"tconst":"%s","titleType":"short","primaryTitle":"Carmencita",
         "originalTitle":"Carmencita","isAdult":0,"startYear":1894,"runtimeMinutes":1,
         "genres":"Documentary,Short"}""".formatted(SHORT));
    insert(mongo, "title_basics", """
        {"tconst":"%s","titleType":"movie","primaryTitle":"Kiss Me Deadly",
         "originalTitle":"Kiss Me Deadly","isAdult":0,"startYear":1950,"runtimeMinutes":106,
         "genres":"Film-Noir,Crime"}""".formatted(NOIR));
    insert(mongo, "title_basics", """
        {"tconst":"%s","titleType":"movie","primaryTitle":"Some Adult Drama",
         "originalTitle":"Some Adult Drama","isAdult":1,"startYear":2010,"runtimeMinutes":80,
         "genres":"Drama"}""".formatted(ADULT));

    insert(mongo, "title_ratings",
        "{\"tconst\":\"%s\",\"averageRating\":9.2,\"numVotes\":2000000}".formatted(GOT));
    insert(mongo, "title_ratings",
        "{\"tconst\":\"%s\",\"averageRating\":9.5,\"numVotes\":1900000}".formatted(BB));
    insert(mongo, "title_ratings",
        "{\"tconst\":\"%s\",\"averageRating\":8.0,\"numVotes\":50000}".formatted(NOIR));
    insert(mongo, "title_ratings",
        "{\"tconst\":\"%s\",\"averageRating\":5.0,\"numVotes\":500}".formatted(ADULT));

    insert(mongo, "name_basics", """
        {"nconst":"nm0000001","primaryName":"Emilia Clarke","birthYear":1986,
         "primaryProfession":"actress,producer","knownForTitles":"%s"}""".formatted(GOT));
    insert(mongo, "name_basics", """
        {"nconst":"nm0000002","primaryName":"Bryan Cranston","birthYear":1956,
         "primaryProfession":"actor,producer","knownForTitles":"%s,%s"}""".formatted(GOT, BB));
    insert(mongo, "name_basics", """
        {"nconst":"nm0000003","primaryName":"Noir Star","birthYear":1920,"deathYear":1990,
         "primaryProfession":"actor","knownForTitles":"%s"}""".formatted(NOIR));
    insert(mongo, "name_basics",
        "{\"nconst\":\"nm0000004\",\"primaryName\":\"No Votes Person\"}");

    insert(mongo, "title_principals", """
        {"tconst":"%s","ordering":1,"nconst":"nm0000001","category":"actress",
         "characters":"[\\"Daenerys Targaryen\\"]"}""".formatted(GOT));
    insert(mongo, "title_principals",
        "{\"tconst\":\"%s\",\"ordering\":2,\"nconst\":\"nm0000002\",\"category\":\"actor\"}"
            .formatted(GOT));
    insert(mongo, "title_principals",
        "{\"tconst\":\"%s\",\"ordering\":1,\"nconst\":\"nm0000002\",\"category\":\"actor\"}"
            .formatted(BB));
    insert(mongo, "title_principals",
        "{\"tconst\":\"%s\",\"ordering\":1,\"nconst\":\"nm0000003\",\"category\":\"actor\"}"
            .formatted(NOIR));
    insert(mongo, "title_principals",
        "{\"tconst\":\"%s\",\"ordering\":1,\"nconst\":\"nm0000004\",\"category\":\"actor\"}"
            .formatted(ADULT));

    insert(mongo, "title_akas", """
        {"titleId":"%s","ordering":1,"title":"Game of Thrones","types":"original",
         "isOriginalTitle":1}""".formatted(GOT));
    insert(mongo, "title_akas", """
        {"titleId":"%s","ordering":2,"title":"Juego de tronos","region":"ES",
         "language":"es","isOriginalTitle":0}""".formatted(GOT));
  }

  private static void insert(MongoTemplate mongo, String collection, String json) {
    mongo.insert(Document.parse(json), collection);
  }
}

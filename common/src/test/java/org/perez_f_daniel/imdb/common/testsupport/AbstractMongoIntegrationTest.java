package org.perez_f_daniel.imdb.common.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Mongo container shared by every integration test in a module.
 * Started once per JVM; Testcontainers' Ryuk reaps it after the run.
 */
public abstract class AbstractMongoIntegrationTest {

  protected static final MongoDBContainer MONGO =
      new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

  static {
    MONGO.start();
  }

  @DynamicPropertySource
  static void mongoProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("imdb_test"));
  }
}

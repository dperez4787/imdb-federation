package org.perez_f_daniel.imdb.common.testsupport;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Import in a @SpringBootTest to register the command counter on the Mongo client. */
@TestConfiguration
public class MongoCounterTestConfig {

  @Bean
  public MongoCommandCounter mongoCommandCounter() {
    return new MongoCommandCounter();
  }

  @Bean
  public MongoClientSettingsBuilderCustomizer mongoCommandCounterCustomizer(MongoCommandCounter counter) {
    return builder -> builder.addCommandListener(counter);
  }
}

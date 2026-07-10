package org.perez_f_daniel.imdb.common.mongo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@AutoConfiguration
public class ImdbCommonAutoConfiguration {

  /** Bounded pool for DataLoader batch queries (sync Mongo driver calls). */
  @Bean(name = "imdbLoaderExecutor", destroyMethod = "shutdown")
  @ConditionalOnMissingBean(name = "imdbLoaderExecutor")
  public ExecutorService imdbLoaderExecutor() {
    AtomicInteger n = new AtomicInteger();
    return Executors.newFixedThreadPool(4, r -> {
      Thread t = new Thread(r, "imdb-loader-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Connection budget: 7 services x N Cloud Run instances share one M10 Atlas cluster
   * (~1500 connection cap). Runs before Boot's standard customizer (order 0) so any
   * pool options in the URI still win.
   */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public MongoClientSettingsBuilderCustomizer imdbConnectionPoolDefaults() {
    return builder -> builder.applyToConnectionPoolSettings(pool -> pool
        .maxSize(15)
        .minSize(0)
        .maxConnectionIdleTime(60, TimeUnit.SECONDS)
        .maxConnecting(2));
  }
}

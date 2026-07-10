package org.perez_f_daniel.imdb.common.testsupport;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts Mongo commands by name so tests can prove DataLoader batching:
 * N entity representations must produce exactly one find/aggregate.
 */
public class MongoCommandCounter implements CommandListener {

  private final ConcurrentMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

  @Override
  public void commandStarted(CommandStartedEvent event) {
    counts.computeIfAbsent(event.getCommandName(), k -> new AtomicInteger()).incrementAndGet();
  }

  public int count(String commandName) {
    AtomicInteger c = counts.get(commandName);
    return c == null ? 0 : c.get();
  }

  public void reset() {
    counts.clear();
  }
}

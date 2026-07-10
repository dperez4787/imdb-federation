package org.perez_f_daniel.imdb.orchestrator.search;

public record CountResult(int total, boolean capped) {

  public static CountResult of(int rawCount, int cap) {
    return rawCount > cap ? new CountResult(cap, true) : new CountResult(rawCount, false);
  }
}

package org.perez_f_daniel.imdb.common;

/**
 * DataLoader key for grouped one-to-many lookups with pagination. The page args
 * must be part of the key or batching would mix pages across parents.
 */
public record GroupPage(String key, int limit, int offset) {

  public PageArgs page() {
    return new PageArgs(limit, offset);
  }
}

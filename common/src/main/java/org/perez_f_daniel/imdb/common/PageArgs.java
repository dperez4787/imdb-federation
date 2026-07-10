package org.perez_f_daniel.imdb.common;

/** Clamped offset/limit pagination arguments. */
public record PageArgs(int limit, int offset) {

  public static PageArgs clamp(Integer limit, Integer offset, int defaultLimit, int maxLimit) {
    int l = limit == null ? defaultLimit : Math.min(Math.max(limit, 1), maxLimit);
    int o = offset == null ? 0 : Math.max(offset, 0);
    return new PageArgs(l, o);
  }
}

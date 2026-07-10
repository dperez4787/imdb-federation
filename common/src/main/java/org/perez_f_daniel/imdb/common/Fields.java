package org.perez_f_daniel.imdb.common;

/** Conversions for IMDb column encodings. */
public final class Fields {

  private Fields() {}

  /** IMDb boolean columns are int32 0/1; absent stays null. */
  public static Boolean toBoolean(Integer flag) {
    return flag == null ? null : flag != 0;
  }

  /** numVotes is stored int64 but fits GraphQL Int (~3M today); guard defensively. */
  public static Integer toInt(Long value) {
    if (value == null) {
      return null;
    }
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
  }
}

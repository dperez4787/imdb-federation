package org.perez_f_daniel.imdb.orchestrator.search;

/**
 * User input must never reach $regex un-escaped. Char-wise escaping (rather than
 * Pattern.quote's \Q..\E) keeps the output trivially PCRE2-safe on the server.
 */
public final class Regexes {

  private Regexes() {}

  public static String escape(String input) {
    StringBuilder sb = new StringBuilder(input.length() + 8);
    for (char c : input.toCharArray()) {
      if (!Character.isLetterOrDigit(c)) {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }

  /** Anchored, lowercased prefix pattern for the *Lower index fields. */
  public static String prefix(String input) {
    return "^" + escape(input.strip().toLowerCase());
  }

  /** Lowercased substring pattern (used only over small joined candidate sets). */
  public static String substring(String input) {
    return escape(input.strip().toLowerCase());
  }
}

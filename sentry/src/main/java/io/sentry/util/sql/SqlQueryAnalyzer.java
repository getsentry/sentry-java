package io.sentry.util.sql;

import io.sentry.util.sql.otel.OtelSqlSanitizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sanitizes SQL statements and derives low-cardinality metadata from them.
 *
 * <p>Literal values are replaced with {@code ?} and comments are removed, so the result can be
 * attached to spans without leaking query values. Analysis fails closed: input that cannot be
 * sanitized safely never yields the original statement.
 */
@ApiStatus.Internal
public final class SqlQueryAnalyzer {

  /**
   * Longest input that is analyzed, in characters (D66). The length limit only stops a scan whose
   * output grows, so a single huge literal or comment would otherwise be read to its end on the
   * caller's thread.
   */
  static final int MAX_INPUT_LENGTH = 1024 * 1024;

  private SqlQueryAnalyzer() {}

  /**
   * Analyzes a SQL statement.
   *
   * <p>Does not throw for any input: an internal failure gives a result with status {@link
   * SqlQuery.Status#ERROR} and no text. Input longer than 1 MiB is not analyzed: the result has
   * status {@link SqlQuery.Status#SKIPPED} and no text.
   *
   * @param sql the statement as passed to the database driver, may be {@code null}
   * @param dialect how dialect-specific syntax is interpreted
   * @param maxQueryTextLength maximum length of {@link SqlQuery#getQueryText()}, must be positive.
   *     Longer text is cut at a token boundary and scanning stops there.
   * @return the analysis result, never {@code null}
   */
  public static @NotNull SqlQuery analyze(
      final @Nullable String sql, final @NotNull SqlDialect dialect, final int maxQueryTextLength) {
    if (sql == null) {
      return SqlQuery.EMPTY;
    }
    if (sql.length() > MAX_INPUT_LENGTH) {
      return SqlQuery.SKIPPED;
    }
    try {
      return SqlQueryPostProcessor.process(
          OtelSqlSanitizer.sanitize(sql, dialect, maxQueryTextLength), dialect, maxQueryTextLength);
    } catch (RuntimeException e) {
      // R5: the generated lexer and the post-processing must fail closed. A bug in either must
      // neither crash the caller nor return unsanitized text, so any RuntimeException gives an
      // empty ERROR result. Errors are not caught. The input is never logged: it may contain the
      // values this class exists to remove.
      return SqlQuery.FAILED;
    }
  }
}

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

  private SqlQueryAnalyzer() {}

  /**
   * Analyzes a SQL statement.
   *
   * <p>Never throws for any input.
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
    // Option 06: the vendored OpenTelemetry grammar with our patch; the wrapper follows in step 5.
    final io.sentry.util.sql.otel.SqlQuery result =
        OtelSqlSanitizer.sanitize(sql, dialect, maxQueryTextLength);
    return new SqlQuery(
        result.getQueryText(),
        result.getQuerySummary(),
        result.getStoredProcedureName(),
        result.getStatus());
  }
}

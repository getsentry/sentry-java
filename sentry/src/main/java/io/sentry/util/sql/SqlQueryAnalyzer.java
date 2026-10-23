package io.sentry.util.sql;

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
   * <p>Never throws for any input. When the statement cannot be sanitized safely, {@link
   * SqlQuery#getQueryText()} is {@code null}.
   *
   * @param sql the statement as passed to the database driver, may be {@code null}
   * @param dialect how dialect-specific syntax is interpreted
   * @return the analysis result, never {@code null}
   */
  public static @NotNull SqlQuery analyze(
      final @Nullable String sql, final @NotNull SqlDialect dialect) {
    // Fail-closed stub until an analyzer implementation lands.
    return SqlQuery.EMPTY;
  }
}

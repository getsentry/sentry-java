package io.sentry.util.sql.otel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point into the vendored OpenTelemetry SQL grammar, whose generated lexer is
 * package-private. Only {@link io.sentry.util.sql.SqlQueryAnalyzer} calls it.
 */
@ApiStatus.Internal
public final class OtelSqlSanitizer {

  private OtelSqlSanitizer() {}

  /**
   * Sanitizes {@code sql}. Text longer than {@code maxQueryTextLength} is cut at a token boundary
   * and scanning stops there; the result's status says how the scan ended.
   */
  public static @NotNull SqlQuery sanitize(
      final @NotNull String sql,
      final @NotNull io.sentry.util.sql.SqlDialect dialect,
      final int maxQueryTextLength) {
    return AutoSqlSanitizer.sanitize(sql, new SqlDialect(dialect), maxQueryTextLength);
  }
}

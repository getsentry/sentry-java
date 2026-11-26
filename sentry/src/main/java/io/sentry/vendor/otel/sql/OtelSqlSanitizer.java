package io.sentry.vendor.otel.sql;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point into the vendored OpenTelemetry SQL grammar, whose generated lexer is package-private.
 * Only {@link io.sentry.util.sql.SqlQueryAnalyzer} calls it.
 */
@ApiStatus.Internal
public final class OtelSqlSanitizer {

  private OtelSqlSanitizer() {}

  /**
   * Runs the grammar on {@code sql}. Can throw a {@link RuntimeException} on an internal lexer
   * error; the caller fails closed.
   */
  public static @NotNull SqlQuery sanitize(
      final @NotNull String sql, final @NotNull SqlDialect dialect) {
    return AutoSqlSanitizer.sanitize(sql, dialect);
  }
}

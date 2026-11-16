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

  public static @NotNull SqlQuery sanitize(
      final @NotNull String sql, final @NotNull io.sentry.util.sql.SqlDialect dialect) {
    return AutoSqlSanitizer.sanitize(sql, new SqlDialect(dialect));
  }
}

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
      final @NotNull String sql, final boolean doubleQuotesAreIdentifiers) {
    return AutoSqlSanitizer.sanitize(
        sql,
        doubleQuotesAreIdentifiers
            ? SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS
            : SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }
}

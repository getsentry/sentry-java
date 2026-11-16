package io.sentry.util.sql.otel;

/**
 * Stand-in for upstream's AutoValue {@code SqlDialect}, so the vendored grammar compiles with its
 * {@code dialect.doubleQuotesAreIdentifiers()} calls unchanged.
 */
final class SqlDialect {

  static final SqlDialect DOUBLE_QUOTES_ARE_STRING_LITERALS = new SqlDialect(false);
  static final SqlDialect DOUBLE_QUOTES_ARE_IDENTIFIERS = new SqlDialect(true);

  private final boolean doubleQuotesAreIdentifiers;

  private SqlDialect(final boolean doubleQuotesAreIdentifiers) {
    this.doubleQuotesAreIdentifiers = doubleQuotesAreIdentifiers;
  }

  boolean doubleQuotesAreIdentifiers() {
    return doubleQuotesAreIdentifiers;
  }
}

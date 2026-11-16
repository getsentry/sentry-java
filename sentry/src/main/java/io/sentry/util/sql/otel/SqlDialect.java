package io.sentry.util.sql.otel;

import io.sentry.util.sql.SqlDialect.Support;
import org.jetbrains.annotations.NotNull;

/**
 * Stand-in for upstream's AutoValue {@code SqlDialect}, so the vendored grammar compiles with its
 * {@code dialect.doubleQuotesAreIdentifiers()} calls unchanged. It exposes the rules of {@link
 * io.sentry.util.sql.SqlDialect} that the patched grammar reads. {@code nestedBlockComments} is
 * left out: a nested {@code /*} stops in every dialect (D35).
 */
final class SqlDialect {

  private final @NotNull io.sentry.util.sql.SqlDialect dialect;

  SqlDialect(final @NotNull io.sentry.util.sql.SqlDialect dialect) {
    this.dialect = dialect;
  }

  /** Upstream's only dialect flag. {@code UNKNOWN} reads double quotes as string literals. */
  boolean doubleQuotesAreIdentifiers() {
    return dialect.getDoubleQuotedIdentifiers() == Support.YES;
  }

  @NotNull
  Support bracketIdentifiers() {
    return dialect.getBracketIdentifiers();
  }

  @NotNull
  Support backslashEscapes() {
    return dialect.getBackslashEscapes();
  }

  @NotNull
  Support dollarQuotedStrings() {
    return dialect.getDollarQuotedStrings();
  }

  @NotNull
  Support taggedDollarQuotes() {
    return dialect.getTaggedDollarQuotes();
  }

  @NotNull
  Support hashComments() {
    return dialect.getHashComments();
  }

  @NotNull
  Support doubleSlashComments() {
    return dialect.getDoubleSlashComments();
  }

  @NotNull
  Support dashDashCommentNeedsWhitespace() {
    return dialect.getDashDashCommentNeedsWhitespace();
  }

  @NotNull
  Support alternativeQuoting() {
    return dialect.getAlternativeQuoting();
  }
}

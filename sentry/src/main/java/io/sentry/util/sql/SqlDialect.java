package io.sentry.util.sql;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes how {@link SqlQueryAnalyzer} interprets dialect-specific syntax.
 *
 * <p>For each flag, the {@link #CONSERVATIVE} value is the one where a wrong guess redacts too much
 * rather than leaking query values.
 */
@ApiStatus.Internal
public final class SqlDialect {

  /** Dialect for unknown database systems. */
  public static final @NotNull SqlDialect CONSERVATIVE =
      new SqlDialect(
          /* doubleQuotesAreIdentifiers= */ false,
          /* backslashEscapes= */ true,
          /* hashComments= */ true,
          /* nestedBlockComments= */ true,
          /* dollarQuotedStrings= */ true);

  private final boolean doubleQuotesAreIdentifiers;
  private final boolean backslashEscapes;
  private final boolean hashComments;
  private final boolean nestedBlockComments;
  private final boolean dollarQuotedStrings;

  private SqlDialect(
      final boolean doubleQuotesAreIdentifiers,
      final boolean backslashEscapes,
      final boolean hashComments,
      final boolean nestedBlockComments,
      final boolean dollarQuotedStrings) {
    this.doubleQuotesAreIdentifiers = doubleQuotesAreIdentifiers;
    this.backslashEscapes = backslashEscapes;
    this.hashComments = hashComments;
    this.nestedBlockComments = nestedBlockComments;
    this.dollarQuotedStrings = dollarQuotedStrings;
  }

  /**
   * Returns the dialect for a {@code db.system} value, or {@link #CONSERVATIVE} if the system is
   * unknown.
   */
  public static @NotNull SqlDialect fromDbSystem(final @Nullable String dbSystem) {
    return CONSERVATIVE;
  }

  /** Whether {@code "..."} delimits an identifier rather than a string literal. */
  public boolean isDoubleQuotesAreIdentifiers() {
    return doubleQuotesAreIdentifiers;
  }

  /** Whether a backslash escapes the next character inside a string literal. */
  public boolean isBackslashEscapes() {
    return backslashEscapes;
  }

  /** Whether {@code #} starts a comment that runs to the end of the line. */
  public boolean isHashComments() {
    return hashComments;
  }

  /** Whether block comments nest, as in PostgreSQL and SQL Server. */
  public boolean isNestedBlockComments() {
    return nestedBlockComments;
  }

  /** Whether {@code $$...$$} and {@code $tag$...$tag$} delimit string literals. */
  public boolean isDollarQuotedStrings() {
    return dollarQuotedStrings;
  }
}

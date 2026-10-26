package io.sentry.util.sql;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes how {@link SqlQueryAnalyzer} interprets dialect-specific syntax.
 *
 * <p>Each rule is {@link Support#YES} or {@link Support#NO} when the database documents fixed
 * behaviour, and {@link Support#UNKNOWN} when the behaviour is undocumented or configurable. For
 * {@code UNKNOWN}, the analyzer either picks the interpretation that can only over-redact, or stops
 * at the construct if every interpretation could leak. Each getter documents which one applies.
 */
@ApiStatus.Internal
public final class SqlDialect {

  /** Whether a dialect supports a syntax rule. */
  public enum Support {
    YES,
    NO,
    UNKNOWN
  }

  /** Dialect for unknown database systems. Every rule is {@link Support#UNKNOWN}. */
  public static final @NotNull SqlDialect CONSERVATIVE =
      new SqlDialect(
          /* doubleQuotedIdentifiers= */ Support.UNKNOWN,
          /* bracketIdentifiers= */ Support.UNKNOWN,
          /* backslashEscapes= */ Support.UNKNOWN,
          /* dollarQuotedStrings= */ Support.UNKNOWN,
          /* hashComments= */ Support.UNKNOWN,
          /* doubleSlashComments= */ Support.UNKNOWN,
          /* dashDashCommentNeedsWhitespace= */ Support.UNKNOWN,
          /* nestedBlockComments= */ Support.UNKNOWN);

  // Sources for every rule: docs/plans/sanitize-sql-queries/README.md, "Dialect table".

  private static final @NotNull SqlDialect POSTGRESQL =
      new SqlDialect(
          Support.YES,
          Support.NO,
          Support.UNKNOWN, // standard_conforming_strings is configurable
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.YES);

  private static final @NotNull SqlDialect MYSQL =
      new SqlDialect(
          Support.UNKNOWN, // ANSI_QUOTES is configurable
          Support.NO,
          Support.UNKNOWN, // NO_BACKSLASH_ESCAPES is configurable
          Support.NO,
          Support.YES,
          Support.NO,
          Support.YES,
          Support.UNKNOWN); // "under some conditions, nested comments might be permitted"

  private static final @NotNull SqlDialect SQLITE =
      new SqlDialect(
          Support.UNKNOWN, // unresolved double-quoted identifiers fall back to string literals
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO);

  private static final @NotNull SqlDialect SQLSERVER =
      new SqlDialect(
          Support.UNKNOWN, // QUOTED_IDENTIFIER is configurable
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.YES);

  private static final @NotNull SqlDialect ORACLE =
      new SqlDialect(
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.UNKNOWN); // nesting is undocumented

  private static final @NotNull SqlDialect H2 =
      new SqlDialect(
          Support.YES,
          Support.UNKNOWN, // only in MSSQLServer compatibility mode
          Support.NO,
          Support.YES,
          Support.NO,
          Support.YES,
          Support.NO,
          Support.YES);

  private static final @NotNull SqlDialect HSQLDB =
      new SqlDialect(
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.UNKNOWN); // nesting is undocumented

  private static final @NotNull SqlDialect DERBY =
      new SqlDialect(
          Support.YES,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.NO,
          Support.YES);

  private final @NotNull Support doubleQuotedIdentifiers;
  private final @NotNull Support bracketIdentifiers;
  private final @NotNull Support backslashEscapes;
  private final @NotNull Support dollarQuotedStrings;
  private final @NotNull Support hashComments;
  private final @NotNull Support doubleSlashComments;
  private final @NotNull Support dashDashCommentNeedsWhitespace;
  private final @NotNull Support nestedBlockComments;

  private SqlDialect(
      final @NotNull Support doubleQuotedIdentifiers,
      final @NotNull Support bracketIdentifiers,
      final @NotNull Support backslashEscapes,
      final @NotNull Support dollarQuotedStrings,
      final @NotNull Support hashComments,
      final @NotNull Support doubleSlashComments,
      final @NotNull Support dashDashCommentNeedsWhitespace,
      final @NotNull Support nestedBlockComments) {
    this.doubleQuotedIdentifiers = doubleQuotedIdentifiers;
    this.bracketIdentifiers = bracketIdentifiers;
    this.backslashEscapes = backslashEscapes;
    this.dollarQuotedStrings = dollarQuotedStrings;
    this.hashComments = hashComments;
    this.doubleSlashComments = doubleSlashComments;
    this.dashDashCommentNeedsWhitespace = dashDashCommentNeedsWhitespace;
    this.nestedBlockComments = nestedBlockComments;
  }

  /**
   * Returns the dialect for a {@code db.system} value as reported by the Sentry JDBC and SQLite
   * integrations, or {@link #CONSERVATIVE} if the system is unknown.
   */
  public static @NotNull SqlDialect fromDbSystem(final @Nullable String dbSystem) {
    if (dbSystem == null) {
      return CONSERVATIVE;
    }
    switch (dbSystem.toLowerCase(Locale.ROOT)) {
      case "postgresql":
        return POSTGRESQL;
      case "mysql":
      case "mariadb":
        return MYSQL;
      case "sqlite":
      case "in-memory":
        return SQLITE;
      case "sqlserver":
        return SQLSERVER;
      case "oracle":
        return ORACLE;
      case "h2":
        return H2;
      case "hsqldb":
        return HSQLDB;
      case "derby":
        return DERBY;
      default:
        return CONSERVATIVE;
    }
  }

  /** Whether {@code "..."} delimits an identifier. {@code UNKNOWN}: treated as a string literal. */
  public @NotNull Support getDoubleQuotedIdentifiers() {
    return doubleQuotedIdentifiers;
  }

  /**
   * Whether {@code [...]} delimits an identifier. {@code UNKNOWN}: brackets are plain punctuation.
   */
  public @NotNull Support getBracketIdentifiers() {
    return bracketIdentifiers;
  }

  /**
   * Whether a backslash escapes the next character in a quoted literal. {@code UNKNOWN}: analysis
   * stops at a backslash inside a quoted literal.
   */
  public @NotNull Support getBackslashEscapes() {
    return backslashEscapes;
  }

  /**
   * Whether {@code $$...$$} and {@code $tag$...$tag$} delimit string literals. {@code UNKNOWN}:
   * treated as string literals.
   */
  public @NotNull Support getDollarQuotedStrings() {
    return dollarQuotedStrings;
  }

  /**
   * Whether {@code #} starts a comment that runs to the end of the line. {@code UNKNOWN}: analysis
   * stops at a {@code #} outside a literal, identifier or comment.
   */
  public @NotNull Support getHashComments() {
    return hashComments;
  }

  /**
   * Whether {@code //} starts a comment that runs to the end of the line. {@code UNKNOWN}: analysis
   * stops at a {@code //} outside a literal or comment.
   */
  public @NotNull Support getDoubleSlashComments() {
    return doubleSlashComments;
  }

  /**
   * Whether {@code --} only starts a comment when followed by whitespace or a control character.
   * {@code UNKNOWN}: analysis stops at a {@code --} followed by any other character.
   */
  public @NotNull Support getDashDashCommentNeedsWhitespace() {
    return dashDashCommentNeedsWhitespace;
  }

  /**
   * Whether block comments nest. {@code UNKNOWN}: analysis stops at a {@code /*} inside a block
   * comment.
   */
  public @NotNull Support getNestedBlockComments() {
    return nestedBlockComments;
  }
}

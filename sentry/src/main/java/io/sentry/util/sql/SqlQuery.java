package io.sentry.util.sql;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Result of {@link SqlQueryAnalyzer#analyze(String, SqlDialect, int)}. */
@ApiStatus.Internal
public final class SqlQuery {

  /** How the analysis of a statement ended. */
  public enum Status {
    /** The whole input was scanned. */
    COMPLETE,
    /** The maximum query text length was reached; the text is cut at a token boundary. */
    TRUNCATED,
    /**
     * The analyzer stopped early at input it cannot sanitize safely, such as an unterminated or
     * ambiguous construct or an unrecognized statement. The text ends with {@code ?}.
     */
    STOPPED,
    /** Analysis failed internally; the text is {@code null}. */
    ERROR
  }

  /** Result for {@code null} input: there was nothing to scan. */
  static final @NotNull SqlQuery EMPTY = new SqlQuery(null, null, null, Status.COMPLETE);

  /** Result after an internal error. */
  static final @NotNull SqlQuery FAILED = new SqlQuery(null, null, null, Status.ERROR);

  private final @Nullable String queryText;
  private final @Nullable String querySummary;
  private final @Nullable String storedProcedureName;
  private final @NotNull Status status;

  SqlQuery(
      final @Nullable String queryText,
      final @Nullable String querySummary,
      final @Nullable String storedProcedureName,
      final @NotNull Status status) {
    this.queryText = queryText;
    this.querySummary = querySummary;
    this.storedProcedureName = storedProcedureName;
    this.status = status;
  }

  /**
   * Returns the sanitized statement, or {@code null} if the input was {@code null} or analysis
   * failed internally.
   *
   * <p>If the analyzer stopped early, for example at an unterminated literal, the text is the
   * sanitized part up to that point followed by {@code ?}.
   */
  public @Nullable String getQueryText() {
    return queryText;
  }

  /**
   * Returns a low-cardinality summary of operations and targets, such as {@code SELECT users}, or
   * {@code null} if nothing was recognized.
   */
  public @Nullable String getQuerySummary() {
    return querySummary;
  }

  /**
   * Returns the stored procedure invoked by {@code CALL}, {@code EXEC} or {@code EXECUTE}, or
   * {@code null}.
   */
  public @Nullable String getStoredProcedureName() {
    return storedProcedureName;
  }

  /**
   * Returns how the analysis ended. A trailing {@code ?} in the text can be a parameter marker of a
   * complete statement or the mark of an early stop; the status tells them apart.
   */
  public @NotNull Status getStatus() {
    return status;
  }
}

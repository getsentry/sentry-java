package io.sentry.util.sql;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Result of {@link SqlQueryAnalyzer#analyze(String, String, int)}. */
@ApiStatus.Internal
public final class SqlQuery {

  static final @NotNull SqlQuery EMPTY = new SqlQuery(null, null, null);

  private final @Nullable String queryText;
  private final @Nullable String querySummary;
  private final @Nullable String storedProcedureName;

  SqlQuery(
      final @Nullable String queryText,
      final @Nullable String querySummary,
      final @Nullable String storedProcedureName) {
    this.queryText = queryText;
    this.querySummary = querySummary;
    this.storedProcedureName = storedProcedureName;
  }

  /**
   * Returns the sanitized statement, or {@code null} if the input was {@code null}, longer than 1
   * MiB, or analysis failed internally.
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

  /** Returns the stored procedure invoked by {@code CALL} or {@code EXECUTE}, or {@code null}. */
  public @Nullable String getStoredProcedureName() {
    return storedProcedureName;
  }
}

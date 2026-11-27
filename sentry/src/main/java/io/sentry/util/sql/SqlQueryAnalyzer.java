package io.sentry.util.sql;

import io.sentry.vendor.otel.sql.OtelSqlSanitizer;
import io.sentry.vendor.otel.sql.SqlDialect;
import io.sentry.vendor.otel.sql.StringUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sanitizes SQL statements with OpenTelemetry Java's SQL grammar, vendored unchanged in {@code
 * io.sentry.vendor.otel.sql}, and derives a low-cardinality summary and the stored procedure name.
 *
 * <p>Literal values are replaced with {@code ?} as upstream replaces them; comments and the gaps
 * upstream has are kept as upstream keeps them. This class adds only what does not depend on SQL
 * syntax: {@code null} and oversized input, the dialect choice, the length limit and failing
 * closed.
 */
@ApiStatus.Internal
public final class SqlQueryAnalyzer {

  /**
   * Longest input that is analyzed, in characters. The grammar reads a single huge literal or
   * comment to its end on the caller's thread, however short the output.
   */
  static final int MAX_INPUT_LENGTH = 1024 * 1024;

  private SqlQueryAnalyzer() {}

  /**
   * Analyzes a SQL statement.
   *
   * <p>Does not throw a {@link RuntimeException} for any input: an internal failure gives a result
   * without text, summary or stored procedure, and the input is not logged. Input longer than 1 MiB
   * is not analyzed and gives the same empty result.
   *
   * @param sql the statement as passed to the database driver, may be {@code null}
   * @param dbSystemName the OpenTelemetry {@code db.system.name} of the database, as {@link
   *     SqlSpanData#toDbSystemName(String)} returns it, or {@code null} if unknown. It decides
   *     whether double quotes delimit identifiers or literals.
   * @param maxQueryTextLength maximum length of {@link SqlQuery#getQueryText()}, must be positive.
   *     Longer text is cut at the last whitespace within the limit.
   * @return the analysis result, never {@code null}
   */
  public static @NotNull SqlQuery analyze(
      final @Nullable String sql,
      final @Nullable String dbSystemName,
      final int maxQueryTextLength) {
    if (sql == null || sql.length() > MAX_INPUT_LENGTH) {
      return SqlQuery.EMPTY;
    }
    try {
      final @NotNull io.sentry.vendor.otel.sql.SqlQuery result =
          OtelSqlSanitizer.sanitize(sql, SqlDialect.forDbSystemName(dbSystemName));
      return new SqlQuery(
          cut(result.getQueryText(), maxQueryTextLength),
          result.getQuerySummary(),
          result.getStoredProcedureName());
    } catch (RuntimeException e) {
      // A bug in the vendored grammar must neither crash the caller nor return unsanitized
      // text, so any RuntimeException gives an empty result. Errors are not caught. The input is
      // never logged: it may contain the values this class exists to remove.
      return SqlQuery.EMPTY;
    }
  }

  /**
   * Cuts sanitized text to {@code maxLength} at the last whitespace within the limit, or at the
   * limit if there is none, without splitting a surrogate pair. Literals are already replaced, so
   * any cut point is safe; whitespace only keeps the last token whole.
   */
  private static @Nullable String cut(final @Nullable String text, final int maxLength) {
    if (text == null || text.length() <= maxLength) {
      return text;
    }
    int end = maxLength;
    while (end > 0 && !Character.isWhitespace(text.charAt(end))) {
      end--;
    }
    return end > 0 ? text.substring(0, end) : StringUtils.truncate(text, maxLength);
  }
}

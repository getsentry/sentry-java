package io.sentry.util.sql;

import io.sentry.ILogger;
import io.sentry.ISpan;
import io.sentry.SentryLevel;
import io.sentry.SpanDataConvention;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The sanitized query data a SQL integration puts on its span: the analyzer's text as description
 * and {@code db.query.text}, plus the summary and stored procedure when present. The raw query is
 * never kept, returned or logged.
 */
@ApiStatus.Internal
public final class SqlSpanData {

  private final @NotNull SqlQuery query;

  private SqlSpanData(final @NotNull SqlQuery query) {
    this.query = query;
  }

  /**
   * Analyzes {@code sql} once, with the dialect for {@code dbSystem}; never throws a {@link
   * RuntimeException}.
   *
   * @param dbSystem the {@code db.system} value the integration reports, mapped with {@link
   *     #toDbSystemName(String)} for the analyzer
   */
  public static @NotNull SqlSpanData analyze(
      final @Nullable String sql,
      final @Nullable String dbSystem,
      final int maxQueryTextLength,
      final @NotNull ILogger logger) {
    final @NotNull SqlQuery query =
        SqlQueryAnalyzer.analyze(sql, toDbSystemName(dbSystem), maxQueryTextLength);
    // No text for input that was analyzed means an internal failure. Null input and input over
    // 1 MiB have no text by design and are not logged.
    if (query.getQueryText() == null
        && sql != null
        && sql.length() <= SqlQueryAnalyzer.MAX_INPUT_LENGTH) {
      logger.log(SentryLevel.DEBUG, "SQL query analysis failed; the span gets no query data.");
    }
    return new SqlSpanData(query);
  }

  /** The span description: the sanitized text, or {@code null} if there is none. */
  public @Nullable String getDescription() {
    return query.getQueryText();
  }

  /**
   * Maps a {@code db.system} value as reported by the Sentry JDBC and SQLite integrations to the
   * OpenTelemetry semantic-conventions {@code db.system.name} value, or {@code null} if there is
   * none. The analyzer chooses its dialect from that value.
   */
  public static @Nullable String toDbSystemName(final @Nullable String dbSystem) {
    if (dbSystem == null) {
      return null;
    }
    final @NotNull String system = dbSystem.toLowerCase(Locale.ROOT);
    switch (system) {
      case "postgresql":
      case "mysql":
      case "mariadb":
      case "sqlite":
      case "hsqldb":
      case "derby":
      case "clickhouse":
        return system;
      case "in-memory":
        return "sqlite";
      case "h2":
        return "h2database";
      case "sqlserver":
        return "microsoft.sql_server";
      case "oracle":
        return "oracle.db";
      case "db2":
        return "ibm.db2";
      case "sap":
        return "sap.hana";
      case "mongo":
        return "mongodb";
      default:
        return null;
    }
  }

  /** Sets the {@code db.query.*} and {@code db.stored_procedure.name} keys that have a value. */
  public void applyTo(final @NotNull ISpan span) {
    final @Nullable String text = query.getQueryText();
    if (text == null) {
      return;
    }
    span.setData(SpanDataConvention.DB_QUERY_TEXT_KEY, text);
    final @Nullable String summary = query.getQuerySummary();
    if (summary != null) {
      span.setData(SpanDataConvention.DB_QUERY_SUMMARY_KEY, summary);
    }
    final @Nullable String storedProcedure = query.getStoredProcedureName();
    if (storedProcedure != null) {
      span.setData(SpanDataConvention.DB_STORED_PROCEDURE_NAME_KEY, storedProcedure);
    }
  }
}

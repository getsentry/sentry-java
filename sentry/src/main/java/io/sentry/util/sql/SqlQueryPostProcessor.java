package io.sentry.util.sql;

import io.sentry.util.sql.SqlDialect.Support;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rules applied to the output of the vendored grammar (option 06, "The wrapper"). The grammar has
 * already replaced literals with {@code ?} and removed comments, so what is left are keywords,
 * names, quoted identifiers, operators and punctuation. These rules only remove text; none of them
 * looks into the original input.
 *
 * <ul>
 *   <li>D12: every statement must start with an allowlisted keyword. Otherwise the statement
 *       becomes {@code ?} and the rest is dropped.
 *   <li>D23: a DCL statement keeps only its operation, followed by {@code ?}. This keys on the
 *       statement start, so it also applies when the length limit cut off the credential clause.
 *   <li>D34: in multi-statement text with a reduced or stopped statement, the summary is {@code
 *       null}, because the grammar's summary entries cannot be matched to statements.
 *   <li>D15: where {@code "} may start a string, a double-quoted summary target becomes {@code "?"}
 *       and a double-quoted procedure name is no stored procedure.
 *   <li>The text is trimmed.
 * </ul>
 */
final class SqlQueryPostProcessor {

  /** README Output conventions, "Unrecognized input (D12)". */
  private static final Set<String> FIRST_KEYWORDS =
      new HashSet<>(
          Arrays.asList(
              "SELECT",
              "INSERT",
              "UPDATE",
              "DELETE",
              "MERGE",
              "UPSERT",
              "REPLACE",
              "WITH",
              "VALUES",
              "TABLE",
              "FROM",
              "CALL",
              "EXEC",
              "EXECUTE",
              "DO",
              "EXPLAIN",
              "CREATE",
              "ALTER",
              "DROP",
              "TRUNCATE",
              "RENAME",
              "COMMENT",
              "GRANT",
              "REVOKE",
              "BEGIN",
              "START",
              "COMMIT",
              "END",
              "ROLLBACK",
              "SAVEPOINT",
              "RELEASE",
              "SET",
              "USE",
              "SHOW",
              "DESCRIBE",
              "DESC",
              "LOCK",
              "UNLOCK",
              "ANALYZE",
              "VACUUM",
              "REINDEX",
              "PRAGMA",
              "ATTACH",
              "DETACH",
              "DECLARE",
              "IF",
              "FETCH",
              "CLOSE",
              "PREPARE",
              "DEALLOCATE",
              "COPY",
              "REFRESH",
              "OPTIMIZE",
              "CHECKPOINT",
              "KILL",
              "FLUSH",
              "RESET",
              "DISCARD",
              "ABORT",
              "XA",
              "LISTEN",
              "UNLISTEN",
              "NOTIFY"));

  private SqlQueryPostProcessor() {}

  static @NotNull SqlQuery process(
      final @NotNull io.sentry.util.sql.otel.SqlQuery result,
      final @NotNull SqlDialect dialect,
      final int maxQueryTextLength) {
    final String text = result.getQueryText();
    if (text == null) {
      return SqlQuery.FAILED;
    }
    final boolean bracketQuotes = dialect.getBracketIdentifiers() == Support.YES;
    final StringBuilder out = new StringBuilder(text.length());
    int statements = 0;
    boolean reduced = false;
    boolean stopped = false;
    @Nullable String operation = null;
    int start = 0;
    while (true) {
      final int end = statementEnd(text, start, bracketQuotes);
      final int first = skipWhitespace(text, start, text.length());
      if (end < 0 || first < end) {
        statements++;
        out.append(text, start, first);
        final int keyword = skipOpenParens(text, first, end < 0 ? text.length() : end);
        final int keywordEnd = wordEnd(text, keyword);
        final boolean cutBeforeKeyword =
            result.getStatus() == SqlQuery.Status.TRUNCATED && isCutBeforeKeyword(text, keyword);
        if (end < 0 || !(cutBeforeKeyword || startsAllowedStatement(text, keyword, keywordEnd))) {
          // D12, or a quote the scanner cannot close: fail closed at the statement start.
          out.append('?');
          stopped = true;
          break;
        }
        final int operationEnd = dclOperationEnd(text, keyword, keywordEnd, end);
        if (operationEnd < 0) {
          out.append(text, first, end);
        } else {
          // D23: the operation as written, then ? for everything up to the statement end.
          out.append(text, first, operationEnd);
          if (skipWhitespace(text, operationEnd, end) < end) {
            out.append(" ?");
          }
          operation = text.substring(keyword, operationEnd);
          reduced = true;
        }
      } else {
        out.append(text, start, end);
      }
      if (end == text.length()) {
        break;
      }
      out.append(';');
      start = end + 1;
    }

    @Nullable String summary = result.getQuerySummary();
    @Nullable String storedProcedure = result.getStoredProcedureName();
    if (stopped) {
      // The stopped statement and everything after it were refused, and the grammar's summary and
      // stored procedure may come from there.
      summary = null;
      storedProcedure = null;
    } else if (reduced) {
      summary = statements > 1 ? null : operation;
    }
    if (dialect.getDoubleQuotedIdentifiers() != Support.YES) {
      if (summary != null && summary.indexOf('"') >= 0) {
        summary = redactDoubleQuotedNames(summary, bracketQuotes);
      }
      if (storedProcedure != null && storedProcedure.indexOf('"') >= 0) {
        storedProcedure = null;
      }
    }

    String queryText = trim(out.toString());
    SqlQuery.Status status = stopped ? SqlQuery.Status.STOPPED : result.getStatus();
    if (queryText.length() > maxQueryTextLength) {
      // Only a reduction of a statement whose rest was one character can grow the text: cut it at
      // a token boundary, as the grammar does.
      final int cut = queryText.lastIndexOf(' ', maxQueryTextLength);
      queryText = cut > 0 ? trim(queryText.substring(0, cut)) : "";
      status = SqlQuery.Status.TRUNCATED;
    }
    return new SqlQuery(queryText, summary, storedProcedure, status);
  }

  /**
   * Returns the index of the {@code ;} that ends the statement starting at {@code from}, the text
   * length if there is none, or -1 if a quoted identifier does not close. Skips the quoted
   * identifiers the grammar keeps: {@code "…"} and {@code `…`}, and {@code […]} where brackets
   * quote identifiers. Like the grammar, a bracket identifier ends at its first {@code ]}; the
   * grammar stops where another {@code ]} follows (T-SQL's escape), so the text holds no {@code ]]}
   * directly after a bracket identifier.
   */
  private static int statementEnd(
      final @NotNull String text, final int from, final boolean bracketQuotes) {
    for (int i = from; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ';') {
        return i;
      }
      final char close = closingQuote(c, bracketQuotes);
      if (close != 0) {
        i = text.indexOf(close, i + 1);
        if (i < 0) {
          return -1;
        }
      }
    }
    return text.length();
  }

  /** The character that closes a quoted identifier opened by {@code c}, or 0. */
  private static char closingQuote(final char c, final boolean bracketQuotes) {
    if (c == '"' || c == '`') {
      return c;
    }
    return c == '[' && bracketQuotes ? ']' : 0;
  }

  private static boolean startsAllowedStatement(
      final @NotNull String text, final int keyword, final int keywordEnd) {
    if (keyword < text.length() && text.charAt(keyword) == '{') {
      return isJdbcCallEscape(text, keyword + 1);
    }
    return keywordEnd > keyword
        && FIRST_KEYWORDS.contains(text.substring(keyword, keywordEnd).toUpperCase(Locale.ROOT));
  }

  /**
   * Whether the length limit cut the text before the statement's first keyword: only an opening
   * brace and the start of {@code ? =} are left, if anything.
   */
  private static boolean isCutBeforeKeyword(final @NotNull String text, final int keyword) {
    int i = keyword;
    if (i < text.length() && text.charAt(i) == '{') {
      i = skipWhitespace(text, i + 1, text.length());
      if (i < text.length() && text.charAt(i) == '?') {
        i = skipWhitespace(text, i + 1, text.length());
        if (i < text.length() && text.charAt(i) == '=') {
          i = skipWhitespace(text, i + 1, text.length());
        }
      }
    }
    return i == text.length();
  }

  /** A JDBC call escape: {@code call} or {@code ? = call} after the opening brace. */
  private static boolean isJdbcCallEscape(final @NotNull String text, final int from) {
    int i = skipWhitespace(text, from, text.length());
    if (i < text.length() && text.charAt(i) == '?') {
      i = skipWhitespace(text, i + 1, text.length());
      if (i >= text.length() || text.charAt(i) != '=') {
        return false;
      }
      i = skipWhitespace(text, i + 1, text.length());
    }
    final int end = wordEnd(text, i);
    return end - i == 4 && text.regionMatches(true, i, "call", 0, 4);
  }

  /**
   * Returns the end of the DCL operation (D23) at the statement start, or -1: {@code GRANT}, {@code
   * REVOKE}, {@code CREATE}/{@code ALTER}/{@code DROP} followed by {@code USER}, {@code LOGIN} or
   * {@code ROLE}, and {@code SET PASSWORD}.
   */
  private static int dclOperationEnd(
      final @NotNull String text, final int keyword, final int keywordEnd, final int end) {
    if (isWord(text, keyword, keywordEnd, "GRANT") || isWord(text, keyword, keywordEnd, "REVOKE")) {
      return keywordEnd;
    }
    final boolean ddl =
        isWord(text, keyword, keywordEnd, "CREATE")
            || isWord(text, keyword, keywordEnd, "ALTER")
            || isWord(text, keyword, keywordEnd, "DROP");
    final boolean set = isWord(text, keyword, keywordEnd, "SET");
    if (!ddl && !set) {
      return -1;
    }
    final int second = skipWhitespace(text, keywordEnd, end);
    if (second == keywordEnd) {
      return -1;
    }
    final int secondEnd = wordEnd(text, second);
    if (ddl
        && (isWord(text, second, secondEnd, "USER")
            || isWord(text, second, secondEnd, "LOGIN")
            || isWord(text, second, secondEnd, "ROLE"))) {
      return secondEnd;
    }
    return set && isWord(text, second, secondEnd, "PASSWORD") ? secondEnd : -1;
  }

  private static boolean isWord(
      final @NotNull String text, final int start, final int end, final @NotNull String word) {
    return end - start == word.length() && text.regionMatches(true, start, word, 0, word.length());
  }

  /**
   * Replaces each double-quoted name in the summary with {@code "?"} (D15). Bracket and backtick
   * names are copied, so a {@code "} inside them opens nothing.
   */
  private static @NotNull String redactDoubleQuotedNames(
      final @NotNull String summary, final boolean bracketQuotes) {
    final StringBuilder out = new StringBuilder(summary.length());
    int i = 0;
    while (i < summary.length()) {
      final char c = summary.charAt(i);
      final char close = closingQuote(c, bracketQuotes);
      if (close == 0) {
        out.append(c);
        i++;
        continue;
      }
      int end = summary.indexOf(close, i + 1);
      // A doubled quote inside the name is part of it.
      while (c == '"' && end >= 0 && end + 1 < summary.length() && summary.charAt(end + 1) == '"') {
        end = summary.indexOf('"', end + 2);
      }
      if (end < 0) {
        // The grammar never emits an unterminated name; drop the rest rather than guess.
        break;
      }
      if (c == '"') {
        out.append("\"?\"");
      } else {
        out.append(summary, i, end + 1);
      }
      i = end + 1;
    }
    return out.toString();
  }

  private static int skipWhitespace(final @NotNull String text, final int from, final int end) {
    int i = from;
    while (i < end && isWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int skipOpenParens(final @NotNull String text, final int from, final int end) {
    int i = from;
    while (i < end && (text.charAt(i) == '(' || isWhitespace(text.charAt(i)))) {
      i++;
    }
    return i;
  }

  private static int wordEnd(final @NotNull String text, final int from) {
    int i = from;
    while (i < text.length() && isWordChar(text.charAt(i))) {
      i++;
    }
    return i;
  }

  private static boolean isWordChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  /** D12 whitespace: space, tab, line feed, vertical tab, form feed, carriage return. */
  private static boolean isWhitespace(final char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
  }

  private static @NotNull String trim(final @NotNull String text) {
    int start = 0;
    int end = text.length();
    while (start < end && isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(start, end);
  }
}

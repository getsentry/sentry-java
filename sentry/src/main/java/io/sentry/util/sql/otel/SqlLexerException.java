package io.sentry.util.sql.otel;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown by the generated lexer on an internal JFlex error ({@code %scanerror}) instead of {@link
 * Error}, so that {@link io.sentry.util.sql.SqlQueryAnalyzer} can fail closed (README R5). No input
 * is known to reach it: the grammar's catch-all rule matches every character.
 */
final class SqlLexerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  SqlLexerException(final @NotNull String message) {
    super(message);
  }
}

package io.sentry.jdbc;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.P6Logger;

/**
 * Logger that instead of logging does nothing. Meant to be used to easily disable logging when
 * other event listeners are in use.
 */
public final class NoOpLogger implements P6Logger {
  @Override
  public void logSQL(
      int connectionId,
      final String now,
      long elapsed,
      final Category category,
      String prepared,
      String sql,
      String url) {}

  @Override
  public void logException(Exception e) {}

  @Override
  public void logText(String text) {}

  @Override
  public boolean isCategoryEnabled(Category category) {
    return false;
  }
}

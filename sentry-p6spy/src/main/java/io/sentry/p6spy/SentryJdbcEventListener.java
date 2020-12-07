package io.sentry.p6spy;

import com.jakewharton.nopen.annotation.Open;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.Span;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.sql.SQLException;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;

/** P6Spy JDBC event listener that creates {@link Span}s around database queries. */
@Open
public class SentryJdbcEventListener extends SimpleJdbcEventListener {
  private final @NotNull IHub hub;
  private final @NotNull Map<Integer, ISpan> spans = new WeakHashMap<>();

  public SentryJdbcEventListener(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public void onBeforeAnyExecute(StatementInformation statementInformation) {
    final ISpan parent = hub.getSpan();
    if (parent != null) {
      final Span span = parent.startChild("db.query", statementInformation.getSql());
      spans.put(statementInformation.getConnectionInformation().getConnectionId(), span);
    }
  }

  @Override
  public void onAfterAnyExecute(
      StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
    final ISpan span = spans.get(statementInformation.getConnectionInformation().getConnectionId());
    if (span != null) {
      if (e != null) {
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
      } else {
        span.setStatus(SpanStatus.OK);
      }
      span.finish();
    }
  }
}

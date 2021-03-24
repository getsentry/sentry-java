package io.sentry.dsproxy;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.Span;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.jetbrains.annotations.NotNull;

/** datasource-proxy query execution listener that creates {@link Span}s around database queries. */
@Open
public class SentryQueryExecutionListener implements QueryExecutionListener {
  private final @NotNull IHub hub;
  private final @NotNull Map<String, ISpan> spans = new WeakHashMap<>();

  public SentryQueryExecutionListener(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public void beforeQuery(
      final @NotNull ExecutionInfo execInfo, final @NotNull List<QueryInfo> queryInfoList) {
    final ISpan parent = hub.getSpan();
    if (parent != null) {
      final ISpan span = parent.startChild("db.query", resolveSpanDescription(queryInfoList));
      spans.put(execInfo.getConnectionId(), span);
    }
  }

  private @NotNull String resolveSpanDescription(final @NotNull List<QueryInfo> queryInfoList) {
    return queryInfoList.stream().map(QueryInfo::getQuery).collect(Collectors.joining(","));
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    final ISpan span = spans.get(execInfo.getConnectionId());
    if (span != null) {
      if (execInfo.getThrowable() != null) {
        span.setThrowable(execInfo.getThrowable());
        span.setStatus(SpanStatus.INTERNAL_ERROR);
      } else {
        span.setStatus(SpanStatus.OK);
      }
      span.finish();
    }
  }
}

package io.sentry.jdbc;

import com.jakewharton.nopen.annotation.Open;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.IntegrationName;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.Span;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** P6Spy JDBC event listener that creates {@link Span}s around database queries. */
@Open
public class SentryJdbcEventListener extends SimpleJdbcEventListener implements IntegrationName {
  private final @NotNull IHub hub;
  private static final @NotNull ThreadLocal<ISpan> CURRENT_SPAN = new ThreadLocal<>();

  public SentryJdbcEventListener(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    addPackageAndIntegrationInfo();
  }

  public SentryJdbcEventListener() {
    this(HubAdapter.getInstance());
  }

  @Override
  public void onBeforeAnyExecute(final @NotNull StatementInformation statementInformation) {
    final ISpan parent = hub.getSpan();
    if (parent != null && !parent.isNoOp()) {
      final ISpan span = parent.startChild("db.query", statementInformation.getSql());
      CURRENT_SPAN.set(span);
    }
  }

  @Override
  public void onAfterAnyExecute(
      final @NotNull StatementInformation statementInformation,
      long timeElapsedNanos,
      final @Nullable SQLException e) {
    final ISpan span = CURRENT_SPAN.get();
    if (span != null) {
      if (e != null) {
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
      } else {
        span.setStatus(SpanStatus.OK);
      }
      span.finish();
      CURRENT_SPAN.set(null);
    }
  }

  private void addPackageAndIntegrationInfo() {
    SentryIntegrationPackageStorage.getInstance().addIntegration(getIntegrationName());
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-jdbc", BuildConfig.VERSION_NAME);
  }
}

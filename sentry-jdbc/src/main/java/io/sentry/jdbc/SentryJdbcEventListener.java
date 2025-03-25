package io.sentry.jdbc;

import static io.sentry.SpanDataConvention.DB_NAME_KEY;
import static io.sentry.SpanDataConvention.DB_SYSTEM_KEY;

import com.jakewharton.nopen.annotation.Open;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.Span;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** P6Spy JDBC event listener that creates {@link Span}s around database queries. */
@Open
public class SentryJdbcEventListener extends SimpleJdbcEventListener {
  private static final String TRACE_ORIGIN = "auto.db.jdbc";
  private final @NotNull IScopes scopes;
  private static final @NotNull ThreadLocal<ISpan> CURRENT_SPAN = new ThreadLocal<>();

  private volatile @Nullable DatabaseUtils.DatabaseDetails cachedDatabaseDetails = null;
  protected final @NotNull AutoClosableReentrantLock databaseDetailsLock =
      new AutoClosableReentrantLock();

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-jdbc", BuildConfig.VERSION_NAME);
  }

  public SentryJdbcEventListener(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    addPackageAndIntegrationInfo();
  }

  public SentryJdbcEventListener() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public void onBeforeAnyExecute(final @NotNull StatementInformation statementInformation) {
    final ISpan parent = scopes.getSpan();
    if (parent != null && !parent.isNoOp()) {
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGIN);
      final ISpan span = parent.startChild("db.query", statementInformation.getSql(), spanOptions);
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
      applyDatabaseDetailsToSpan(statementInformation, span);

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
    SentryIntegrationPackageStorage.getInstance().addIntegration("JDBC");
  }

  private void applyDatabaseDetailsToSpan(
      final @NotNull StatementInformation statementInformation, final @NotNull ISpan span) {
    final @NotNull DatabaseUtils.DatabaseDetails databaseDetails =
        getOrComputeDatabaseDetails(statementInformation);

    if (databaseDetails.getDbSystem() != null) {
      span.setData(DB_SYSTEM_KEY, databaseDetails.getDbSystem());
    }

    if (databaseDetails.getDbName() != null) {
      span.setData(DB_NAME_KEY, databaseDetails.getDbName());
    }
  }

  private @NotNull DatabaseUtils.DatabaseDetails getOrComputeDatabaseDetails(
      final @NotNull StatementInformation statementInformation) {
    if (cachedDatabaseDetails == null) {
      try (final @NotNull ISentryLifecycleToken ignored = databaseDetailsLock.acquire()) {
        if (cachedDatabaseDetails == null) {
          cachedDatabaseDetails = DatabaseUtils.readFrom(statementInformation);
        }
      }
    }

    return cachedDatabaseDetails;
  }
}

package io.sentry.jdbc;

import static io.sentry.SpanDataConvention.DB_NAME_KEY;
import static io.sentry.SpanDataConvention.DB_SYSTEM_KEY;

import com.jakewharton.nopen.annotation.Open;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SpanOptions;
import io.sentry.SpanStatus;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.sql.SQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SentryJdbcEventListener extends SimpleJdbcEventListener {
  private static final String TRACE_ORIGIN = "auto.db.jdbc";
  private final @NotNull IScopes scopes;
  private static final @NotNull ThreadLocal<ISpan> CURRENT_QUERY_SPAN = new ThreadLocal<>();
  private static final @NotNull ThreadLocal<ISpan> CURRENT_TRANSACTION_SPAN = new ThreadLocal<>();

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
    startSpan(CURRENT_QUERY_SPAN, "db.query", statementInformation.getSql());
  }

  @Override
  public void onAfterAnyExecute(
      final @NotNull StatementInformation statementInformation,
      long timeElapsedNanos,
      final @Nullable SQLException e) {
    finishSpan(CURRENT_QUERY_SPAN, statementInformation.getConnectionInformation(), e);
  }

  @Override
  public void onBeforeSetAutoCommit(
      final @NotNull ConnectionInformation connectionInformation,
      boolean newAutoCommit,
      boolean currentAutoCommit) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    final boolean isSwitchingToManualCommit = !newAutoCommit && currentAutoCommit;
    if (isSwitchingToManualCommit) {
      startSpan(CURRENT_TRANSACTION_SPAN, "db.sql.transaction.begin", "BEGIN");
    }
  }

  @Override
  public void onAfterSetAutoCommit(
      final @NotNull ConnectionInformation connectionInformation,
      final boolean newAutoCommit,
      final boolean oldAutoCommit,
      final @Nullable SQLException e) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    final boolean isSwitchingToManualCommit = !newAutoCommit && oldAutoCommit;
    if (isSwitchingToManualCommit) {
      finishSpan(CURRENT_TRANSACTION_SPAN, connectionInformation, e);
    }
  }

  @Override
  public void onBeforeCommit(final @NotNull ConnectionInformation connectionInformation) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    startSpan(CURRENT_TRANSACTION_SPAN, "db.sql.transaction.commit", "COMMIT");
  }

  @Override
  public void onAfterCommit(
      final @NotNull ConnectionInformation connectionInformation,
      final long timeElapsedNanos,
      final @Nullable SQLException e) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    finishSpan(CURRENT_TRANSACTION_SPAN, connectionInformation, e);
  }

  @Override
  public void onBeforeRollback(final @NotNull ConnectionInformation connectionInformation) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    startSpan(CURRENT_TRANSACTION_SPAN, "db.sql.transaction.rollback", "ROLLBACK");
  }

  @Override
  public void onAfterRollback(
      final @NotNull ConnectionInformation connectionInformation,
      final long timeElapsedNanos,
      final @Nullable SQLException e) {
    if (!isDatabaseTransactionTracingEnabled()) {
      return;
    }
    finishSpan(CURRENT_TRANSACTION_SPAN, connectionInformation, e);
  }

  private boolean isDatabaseTransactionTracingEnabled() {
    return scopes.getOptions().isEnableDatabaseTransactionTracing();
  }

  private void startSpan(
      final @NotNull ThreadLocal<ISpan> spanHolder,
      final @NotNull String operation,
      final @Nullable String description) {
    final @Nullable ISpan parent = scopes.getSpan();
    if (parent != null && !parent.isNoOp()) {
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGIN);
      final @NotNull ISpan span = parent.startChild(operation, description, spanOptions);
      spanHolder.set(span);
    }
  }

  private void finishSpan(
      final @NotNull ThreadLocal<ISpan> spanHolder,
      final @Nullable ConnectionInformation connectionInformation,
      final @Nullable SQLException e) {
    final @Nullable ISpan span = spanHolder.get();

    if (span != null) {
      applyDatabaseDetailsToSpan(connectionInformation, span);

      if (e != null) {
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
      } else {
        span.setStatus(SpanStatus.OK);
      }
      span.finish();
      spanHolder.remove();
    }
  }

  private void addPackageAndIntegrationInfo() {
    SentryIntegrationPackageStorage.getInstance().addIntegration("JDBC");
  }

  private void applyDatabaseDetailsToSpan(
      final @Nullable ConnectionInformation connectionInformation, final @NotNull ISpan span) {
    final @NotNull DatabaseUtils.DatabaseDetails databaseDetails =
        getOrComputeDatabaseDetails(connectionInformation);

    if (databaseDetails.getDbSystem() != null) {
      span.setData(DB_SYSTEM_KEY, databaseDetails.getDbSystem());
    }

    if (databaseDetails.getDbName() != null) {
      span.setData(DB_NAME_KEY, databaseDetails.getDbName());
    }
  }

  private @NotNull DatabaseUtils.DatabaseDetails getOrComputeDatabaseDetails(
      final @Nullable ConnectionInformation connectionInformation) {
    if (cachedDatabaseDetails == null) {
      try (final @NotNull ISentryLifecycleToken ignored = databaseDetailsLock.acquire()) {
        if (cachedDatabaseDetails == null) {
          cachedDatabaseDetails = DatabaseUtils.readFrom(connectionInformation);
        }
      }
    }

    return cachedDatabaseDetails;
  }
}

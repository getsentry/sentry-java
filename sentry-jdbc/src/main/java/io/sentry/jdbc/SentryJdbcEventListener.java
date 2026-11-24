package io.sentry.jdbc;

import static io.sentry.SpanDataConvention.DB_NAMESPACE_KEY;
import static io.sentry.SpanDataConvention.DB_NAME_KEY;
import static io.sentry.SpanDataConvention.DB_SYSTEM_KEY;
import static io.sentry.SpanDataConvention.DB_SYSTEM_NAME_KEY;

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
import io.sentry.util.SpanUtils;
import io.sentry.util.sql.SqlSpanData;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class SentryJdbcEventListener extends SimpleJdbcEventListener {
  private static final String TRACE_ORIGIN = "auto.db.jdbc";

  /** Maximum length of the sanitized query text (D5, OpenTelemetry Java's value). */
  private static final int MAX_QUERY_TEXT_LENGTH = 32 * 1024;

  private final @NotNull IScopes scopes;
  private static final @NotNull ThreadLocal<ISpan> CURRENT_QUERY_SPAN = new ThreadLocal<>();
  private static final @NotNull ThreadLocal<ISpan> CURRENT_TRANSACTION_SPAN = new ThreadLocal<>();

  /**
   * p6spy shares one listener across all datasources of the JVM, so details are cached per
   * connection URL. Bounded; URLs beyond the bound are parsed on every query. The keys are full
   * connection URLs and may contain credentials: never log them.
   */
  private static final int MAX_CACHED_DATABASE_DETAILS = 64;

  private final @NotNull Map<String, DatabaseUtils.DatabaseDetails> databaseDetailsByUrl =
      new ConcurrentHashMap<>();

  /** Guards insertion into the details cache. */
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
    final @Nullable ISpan parent = getActiveParent();
    if (parent == null) {
      return;
    }
    final @NotNull DatabaseUtils.DatabaseDetails databaseDetails =
        getOrComputeDatabaseDetails(statementInformation.getConnectionInformation());
    final @NotNull SqlSpanData queryData =
        SqlSpanData.analyze(
            statementInformation.getSql(),
            databaseDetails.getDbSystem(),
            MAX_QUERY_TEXT_LENGTH,
            scopes.getOptions().getLogger());
    final @NotNull ISpan span =
        startChild(parent, CURRENT_QUERY_SPAN, "db.query", queryData.getDescription());
    queryData.applyTo(span);
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
    final @Nullable ISpan parent = getActiveParent();
    if (parent != null) {
      startChild(parent, spanHolder, operation, description);
    }
  }

  /**
   * Returns the parent for a JDBC span, or {@code null} if no span would be recorded: no active
   * span, or our origin is ignored (the default under OpenTelemetry), which would only yield a
   * no-op child after the statement was analyzed.
   */
  private @Nullable ISpan getActiveParent() {
    if (SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), TRACE_ORIGIN)) {
      return null;
    }
    final @Nullable ISpan parent = scopes.getSpan();
    return parent != null && !parent.isNoOp() ? parent : null;
  }

  private @NotNull ISpan startChild(
      final @NotNull ISpan parent,
      final @NotNull ThreadLocal<ISpan> spanHolder,
      final @NotNull String operation,
      final @Nullable String description) {
    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setOrigin(TRACE_ORIGIN);
    final @NotNull ISpan span = parent.startChild(operation, description, spanOptions);
    spanHolder.set(span);
    return span;
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

    final @Nullable String dbSystem = databaseDetails.getDbSystem();
    if (dbSystem != null) {
      span.setData(DB_SYSTEM_KEY, dbSystem);
      final @Nullable String dbSystemName = SqlSpanData.toDbSystemName(dbSystem);
      if (dbSystemName != null) {
        span.setData(DB_SYSTEM_NAME_KEY, dbSystemName);
      }
    }

    final @Nullable String dbName = databaseDetails.getDbName();
    if (dbName != null) {
      span.setData(DB_NAME_KEY, dbName);
      span.setData(DB_NAMESPACE_KEY, dbName);
    }
  }

  private @NotNull DatabaseUtils.DatabaseDetails getOrComputeDatabaseDetails(
      final @Nullable ConnectionInformation connectionInformation) {
    final @Nullable String url =
        connectionInformation == null ? null : connectionInformation.getUrl();
    if (url == null) {
      return DatabaseUtils.readFrom(connectionInformation);
    }
    final @Nullable DatabaseUtils.DatabaseDetails cached = databaseDetailsByUrl.get(url);
    if (cached != null) {
      return cached;
    }
    if (databaseDetailsByUrl.size() >= MAX_CACHED_DATABASE_DETAILS) {
      return DatabaseUtils.readFrom(connectionInformation);
    }
    try (final @NotNull ISentryLifecycleToken ignored = databaseDetailsLock.acquire()) {
      final @Nullable DatabaseUtils.DatabaseDetails existing = databaseDetailsByUrl.get(url);
      if (existing != null) {
        return existing;
      }
      final @NotNull DatabaseUtils.DatabaseDetails details =
          DatabaseUtils.readFrom(connectionInformation);
      if (databaseDetailsByUrl.size() < MAX_CACHED_DATABASE_DETAILS) {
        databaseDetailsByUrl.put(url, details);
      }
      return details;
    }
  }
}

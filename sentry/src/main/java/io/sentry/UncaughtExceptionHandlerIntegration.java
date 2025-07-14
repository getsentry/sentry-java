package io.sentry;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.hints.EventDropReason;
import io.sentry.hints.SessionEnd;
import io.sentry.hints.TransactionEnd;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryId;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Sends any uncaught exception to Sentry, then passes the exception on to the pre-existing uncaught
 * exception handler.
 */
public final class UncaughtExceptionHandlerIntegration
    implements Integration, Thread.UncaughtExceptionHandler, Closeable {
  /** Reference to the pre-existing uncaught exception handler. */
  private @Nullable Thread.UncaughtExceptionHandler defaultExceptionHandler;

  private static final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private @Nullable IScopes scopes;
  private @Nullable SentryOptions options;

  private boolean registered = false;
  private final @NotNull UncaughtExceptionHandler threadAdapter;

  public UncaughtExceptionHandlerIntegration() {
    this(UncaughtExceptionHandler.Adapter.getInstance());
  }

  UncaughtExceptionHandlerIntegration(final @NotNull UncaughtExceptionHandler threadAdapter) {
    this.threadAdapter = Objects.requireNonNull(threadAdapter, "threadAdapter is required.");
  }

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull SentryOptions options) {
    if (registered) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "Attempt to register a UncaughtExceptionHandlerIntegration twice.");
      return;
    }
    registered = true;

    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options = Objects.requireNonNull(options, "SentryOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "UncaughtExceptionHandlerIntegration enabled: %s",
            this.options.isEnableUncaughtExceptionHandler());

    if (this.options.isEnableUncaughtExceptionHandler()) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        final Thread.UncaughtExceptionHandler currentHandler =
            threadAdapter.getDefaultUncaughtExceptionHandler();
        if (currentHandler != null) {
          this.options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "default UncaughtExceptionHandler class='"
                      + currentHandler.getClass().getName()
                      + "'");
          if (currentHandler instanceof UncaughtExceptionHandlerIntegration) {
            final UncaughtExceptionHandlerIntegration currentHandlerIntegration =
                (UncaughtExceptionHandlerIntegration) currentHandler;
            if (currentHandlerIntegration.scopes != null
                && scopes.getGlobalScope() == currentHandlerIntegration.scopes.getGlobalScope()) {
              defaultExceptionHandler = currentHandlerIntegration.defaultExceptionHandler;
            } else {
              defaultExceptionHandler = currentHandler;
            }
          } else {
            defaultExceptionHandler = currentHandler;
          }
        }

        threadAdapter.setDefaultUncaughtExceptionHandler(this);
      }

      this.options
          .getLogger()
          .log(SentryLevel.DEBUG, "UncaughtExceptionHandlerIntegration installed.");
      addIntegrationToSdkVersion("UncaughtExceptionHandler");
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable thrown) {
    if (options != null && scopes != null) {
      options.getLogger().log(SentryLevel.INFO, "Uncaught exception received.");

      try {
        final UncaughtExceptionHint exceptionHint =
            new UncaughtExceptionHint(options.getFlushTimeoutMillis(), options.getLogger());
        final Throwable throwable = getUnhandledThrowable(thread, thrown);
        final SentryEvent event = new SentryEvent(throwable);
        event.setLevel(SentryLevel.FATAL);

        final ITransaction transaction = scopes.getTransaction();
        if (transaction == null && event.getEventId() != null) {
          // if there's no active transaction on scope, this event can trigger flush notification
          exceptionHint.setFlushable(event.getEventId());
        }
        final Hint hint = HintUtils.createWithTypeCheckHint(exceptionHint);

        final @NotNull SentryId sentryId = scopes.captureEvent(event, hint);
        final boolean isEventDropped = sentryId.equals(SentryId.EMPTY_ID);
        final EventDropReason eventDropReason = HintUtils.getEventDropReason(hint);
        // in case the event has been dropped by multithreaded deduplicator, the other threads will
        // crash the app without a chance to persist the main event so we have to special-case this
        if (!isEventDropped
            || EventDropReason.MULTITHREADED_DEDUPLICATION.equals(eventDropReason)) {
          // Block until the event is flushed to disk
          if (!exceptionHint.waitFlush()) {
            options
                .getLogger()
                .log(
                    SentryLevel.WARNING,
                    "Timed out waiting to flush event to disk before crashing. Event: %s",
                    event.getEventId());
          }
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Error sending uncaught exception to Sentry.", e);
      }

      if (defaultExceptionHandler != null) {
        options.getLogger().log(SentryLevel.INFO, "Invoking inner uncaught exception handler.");
        defaultExceptionHandler.uncaughtException(thread, thrown);
      } else {
        if (options.isPrintUncaughtStackTrace()) {
          thrown.printStackTrace();
        }
      }
    }
  }

  @TestOnly
  @NotNull
  static Throwable getUnhandledThrowable(
      final @NotNull Thread thread, final @NotNull Throwable thrown) {
    final Mechanism mechanism = new Mechanism();
    mechanism.setHandled(false);
    mechanism.setType("UncaughtExceptionHandler");
    return new ExceptionMechanismException(mechanism, thrown, thread);
  }

  /**
   * Remove this UncaughtExceptionHandlerIntegration from the exception handler chain.
   *
   * <p>If this integration is currently the default handler, restore the initial handler, if this
   * integration is not the current default call removeFromHandlerTree
   */
  @Override
  public void close() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (this == threadAdapter.getDefaultUncaughtExceptionHandler()) {
        threadAdapter.setDefaultUncaughtExceptionHandler(defaultExceptionHandler);

        if (options != null) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "UncaughtExceptionHandlerIntegration removed.");
        }
      } else {
        removeFromHandlerTree(threadAdapter.getDefaultUncaughtExceptionHandler());
      }
    }
  }

  /**
   * Intermediary method before calling the actual recursive method. Used to initialize HashSet to
   * keep track of visited handlers to avoid infinite recursion in case of cycles in the chain.
   */
  private void removeFromHandlerTree(@Nullable Thread.UncaughtExceptionHandler currentHandler) {
    removeFromHandlerTree(currentHandler, new HashSet<>());
  }

  /**
   * Recursively traverses the chain of UncaughtExceptionHandlerIntegrations to find and remove this
   * specific integration instance.
   *
   * <p>Checks if this instance is the defaultExceptionHandler of the current handler, if so replace
   * with its own defaultExceptionHandler, thus removing it from the chain.
   *
   * <p>If not, recursively calls itself on the next handler in the chain.
   *
   * <p>Recursion stops if the current handler is not an instance of
   * UncaughtExceptionHandlerIntegration, the handler was found and removed or a cycle was detected.
   *
   * @param currentHandler The current handler in the chain to examine
   * @param visited Set of already visited handlers to detect cycles
   */
  private void removeFromHandlerTree(
      @Nullable Thread.UncaughtExceptionHandler currentHandler,
      @NotNull Set<Thread.UncaughtExceptionHandler> visited) {

    if (currentHandler == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "Found no UncaughtExceptionHandler to remove.");
      }
      return;
    }

    if (!visited.add(currentHandler)) {
      if (options != null) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Cycle detected in UncaughtExceptionHandler chain while removing handler.");
      }
      return;
    }

    if (!(currentHandler instanceof UncaughtExceptionHandlerIntegration)) {
      return;
    }

    final UncaughtExceptionHandlerIntegration currentHandlerIntegration =
        (UncaughtExceptionHandlerIntegration) currentHandler;

    if (this == currentHandlerIntegration.defaultExceptionHandler) {
      currentHandlerIntegration.defaultExceptionHandler = defaultExceptionHandler;
      if (options != null) {
        options.getLogger().log(SentryLevel.DEBUG, "UncaughtExceptionHandlerIntegration removed.");
      }
    } else {
      removeFromHandlerTree(currentHandlerIntegration.defaultExceptionHandler, visited);
    }
  }

  @Open // open for tests
  @ApiStatus.Internal
  public static class UncaughtExceptionHint extends BlockingFlushHint
      implements SessionEnd, TransactionEnd {

    private final AtomicReference<SentryId> flushableEventId = new AtomicReference<>();

    public UncaughtExceptionHint(final long flushTimeoutMillis, final @NotNull ILogger logger) {
      super(flushTimeoutMillis, logger);
    }

    @Override
    public boolean isFlushable(final @Nullable SentryId eventId) {
      final SentryId unwrapped = flushableEventId.get();
      return unwrapped != null && unwrapped.equals(eventId);
    }

    @Override
    public void setFlushable(final @NotNull SentryId eventId) {
      flushableEventId.set(eventId);
    }
  }
}

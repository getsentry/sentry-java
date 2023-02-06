package io.sentry;

import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/** Registers hook that flushes {@link Hub} when main thread shuts down. */
public final class ShutdownHookIntegration implements Integration, Closeable {

  private final @NotNull Runtime runtime;

  private @Nullable Thread thread;

  @TestOnly
  public ShutdownHookIntegration(final @NotNull Runtime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "Runtime is required");
  }

  public ShutdownHookIntegration() {
    this(Runtime.getRuntime());
  }

  @Override
  public void register(final @NotNull IHub hub, final @NotNull SentryOptions options) {
    Objects.requireNonNull(hub, "Hub is required");
    Objects.requireNonNull(options, "SentryOptions is required");

    if (options.isEnableShutdownHook()) {
      thread = new Thread(() -> hub.flush(options.getFlushTimeoutMillis()));
      runtime.addShutdownHook(thread);
      options.getLogger().log(SentryLevel.DEBUG, "ShutdownHookIntegration installed.");
    } else {
      options.getLogger().log(SentryLevel.INFO, "enableShutdownHook is disabled.");
    }
  }

  @Override
  public void close() throws IOException {
    if (thread != null) {
      try {
        runtime.removeShutdownHook(thread);
      } catch (IllegalStateException e) {
        @Nullable final String message = e.getMessage();
        if (message != null && message.equals("Shutdown in progress")) {
          // ignore
        } else {
          throw e;
        }
      }
    }
  }

  @VisibleForTesting
  @Nullable
  Thread getHook() {
    return thread;
  }
}

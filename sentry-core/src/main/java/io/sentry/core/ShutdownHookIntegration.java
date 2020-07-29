package io.sentry.core;

import io.sentry.core.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/** Registers hook that closes {@link Hub} when main thread shuts down. */
public final class ShutdownHookIntegration implements Integration {

  private final @NotNull Runtime runtime;

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

    runtime.addShutdownHook(new Thread(() -> hub.close()));
  }
}

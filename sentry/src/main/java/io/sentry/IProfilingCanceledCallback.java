package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Notified when the profiler learns that no profile will ever exist for a profiler id, so that
 * anything already tagged with that id can drop the reference before being sent.
 *
 * <p>Implementations are invoked on whichever thread learns about the failure: an OS binder thread,
 * the executor thread running the profiler's result timeout, or the caller that started or stopped
 * the profiler, which may be the main thread. They must not block.
 */
@ApiStatus.Internal
public interface IProfilingCanceledCallback {
  void onProfilingCanceled(final @NotNull SentryId profilerId);
}

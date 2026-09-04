package io.sentry.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The source of wall-clock time.
 *
 * <p>Stamps a moment that will leave this process — an event, a breadcrumb, a session — and nothing
 * else. It deliberately cannot report a duration: measuring belongs to {@link Stopwatch}, and a
 * group of instants that will be subtracted from each other belongs to an {@link AnchoredClock},
 * which reads this once and projects the rest.
 */
@ApiStatus.Internal
public interface EpochClock {

  /** The current instant. Serialize it; do not subtract it from another one. */
  @NotNull
  Timestamp now();
}

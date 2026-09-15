package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Describes a recovered abnormal exit that may end the previous persisted session. */
@ApiStatus.Internal
public interface PreviousSessionAbnormalExit extends Backfillable {

  /** What mechanism caused the previous session to end abnormally. */
  @Nullable
  String mechanism();

  /** When the abnormal exit happened. */
  @NotNull
  Long timestamp();

  /** Whether this exit belongs to the immediately previous session. */
  boolean shouldUpdatePreviousSession();
}

package io.sentry.profiling;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum ProfileRecordingState {
  RECORDED,
  NOT_RECORDED,
  /** The state is unknown, e.g. because an async profiling request is still awaiting an answer. */
  UNKNOWN
}

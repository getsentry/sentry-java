package io.sentry.android.replay

internal enum class ReplayLifecycleState {
  /**
   * Initial state of a Replay session. This is the state when ReplayIntegration is constructed but
   * has not been started yet.
   */
  INITIAL,

  /**
   * Started state for a Replay session. This state is reached after the start() method is called
   * and the recording is initialized successfully.
   */
  STARTED,

  /**
   * Resumed state for a Replay session. This state is reached after resume() is called on an
   * already started recording.
   */
  RESUMED,

  /**
   * Paused state for a Replay session. This state is reached after pause() is called on a resumed
   * recording.
   */
  PAUSED,

  /**
   * Stopped state for a Replay session. This state is reached after stop() is called. The recording
   * can be started again from this state.
   */
  STOPPED,

  /**
   * Closed state for a Replay session. This is the terminal state reached after close() is called.
   * No further state transitions are possible after this.
   */
  CLOSED,
}

internal fun ReplayLifecycleState.isAllowed(newState: ReplayLifecycleState): Boolean =
  when (this) {
    ReplayLifecycleState.INITIAL ->
      newState == ReplayLifecycleState.STARTED || newState == ReplayLifecycleState.CLOSED
    ReplayLifecycleState.STARTED ->
      newState == ReplayLifecycleState.PAUSED ||
        newState == ReplayLifecycleState.STOPPED ||
        newState == ReplayLifecycleState.CLOSED
    ReplayLifecycleState.RESUMED ->
      newState == ReplayLifecycleState.PAUSED ||
        newState == ReplayLifecycleState.STOPPED ||
        newState == ReplayLifecycleState.CLOSED
    ReplayLifecycleState.PAUSED ->
      newState == ReplayLifecycleState.RESUMED ||
        newState == ReplayLifecycleState.STOPPED ||
        newState == ReplayLifecycleState.CLOSED
    ReplayLifecycleState.STOPPED ->
      newState == ReplayLifecycleState.STARTED || newState == ReplayLifecycleState.CLOSED
    ReplayLifecycleState.CLOSED -> false
  }

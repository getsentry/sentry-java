package io.sentry.android.replay

internal enum class ReplayState {
    /**
     * Initial state of a Replay session. This is the state when ReplayIntegration is constructed
     * but has not been started yet.
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
     * Paused state for a Replay session. This state is reached after pause() is called on a
     * resumed recording.
     */
    PAUSED,

    /**
     * Stopped state for a Replay session. This state is reached after stop() is called.
     * The recording can be started again from this state.
     */
    STOPPED,

    /**
     * Closed state for a Replay session. This is the terminal state reached after close() is called.
     * No further state transitions are possible after this.
     */
    CLOSED,
}

/**
 * Class to manage state transitions for ReplayIntegration
 */
internal class ReplayLifecycle {
    @field:Volatile
    internal var currentState = ReplayState.INITIAL

    fun isAllowed(newState: ReplayState): Boolean =
        when (currentState) {
            ReplayState.INITIAL -> newState == ReplayState.STARTED || newState == ReplayState.CLOSED
            ReplayState.STARTED -> newState == ReplayState.PAUSED || newState == ReplayState.STOPPED || newState == ReplayState.CLOSED
            ReplayState.RESUMED -> newState == ReplayState.PAUSED || newState == ReplayState.STOPPED || newState == ReplayState.CLOSED
            ReplayState.PAUSED -> newState == ReplayState.RESUMED || newState == ReplayState.STOPPED || newState == ReplayState.CLOSED
            ReplayState.STOPPED -> newState == ReplayState.STARTED || newState == ReplayState.CLOSED
            ReplayState.CLOSED -> false
        }

    fun isTouchRecordingAllowed(): Boolean = currentState == ReplayState.STARTED || currentState == ReplayState.RESUMED
}

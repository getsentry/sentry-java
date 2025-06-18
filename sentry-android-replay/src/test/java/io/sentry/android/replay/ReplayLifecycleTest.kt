package io.sentry.android.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayLifecycleTest {
    @Test
    fun `verify initial state`() {
        val lifecycle = ReplayLifecycle()
        assertEquals(ReplayState.INITIAL, lifecycle.currentState)
    }

    @Test
    fun `test transitions from INITIAL state`() {
        val lifecycle = ReplayLifecycle()

        assertTrue(lifecycle.isAllowed(ReplayState.STARTED))
        assertTrue(lifecycle.isAllowed(ReplayState.CLOSED))

        assertFalse(lifecycle.isAllowed(ReplayState.RESUMED))
        assertFalse(lifecycle.isAllowed(ReplayState.PAUSED))
        assertFalse(lifecycle.isAllowed(ReplayState.STOPPED))
    }

    @Test
    fun `test transitions from STARTED state`() {
        val lifecycle = ReplayLifecycle()
        lifecycle.currentState = ReplayState.STARTED

        assertTrue(lifecycle.isAllowed(ReplayState.PAUSED))
        assertTrue(lifecycle.isAllowed(ReplayState.STOPPED))
        assertTrue(lifecycle.isAllowed(ReplayState.CLOSED))

        assertFalse(lifecycle.isAllowed(ReplayState.RESUMED))
        assertFalse(lifecycle.isAllowed(ReplayState.INITIAL))
    }

    @Test
    fun `test transitions from RESUMED state`() {
        val lifecycle = ReplayLifecycle()
        lifecycle.currentState = ReplayState.RESUMED

        assertTrue(lifecycle.isAllowed(ReplayState.PAUSED))
        assertTrue(lifecycle.isAllowed(ReplayState.STOPPED))
        assertTrue(lifecycle.isAllowed(ReplayState.CLOSED))

        assertFalse(lifecycle.isAllowed(ReplayState.STARTED))
        assertFalse(lifecycle.isAllowed(ReplayState.INITIAL))
    }

    @Test
    fun `test transitions from PAUSED state`() {
        val lifecycle = ReplayLifecycle()
        lifecycle.currentState = ReplayState.PAUSED

        assertTrue(lifecycle.isAllowed(ReplayState.RESUMED))
        assertTrue(lifecycle.isAllowed(ReplayState.STOPPED))
        assertTrue(lifecycle.isAllowed(ReplayState.CLOSED))

        assertFalse(lifecycle.isAllowed(ReplayState.STARTED))
        assertFalse(lifecycle.isAllowed(ReplayState.INITIAL))
    }

    @Test
    fun `test transitions from STOPPED state`() {
        val lifecycle = ReplayLifecycle()
        lifecycle.currentState = ReplayState.STOPPED

        assertTrue(lifecycle.isAllowed(ReplayState.STARTED))
        assertTrue(lifecycle.isAllowed(ReplayState.CLOSED))

        assertFalse(lifecycle.isAllowed(ReplayState.RESUMED))
        assertFalse(lifecycle.isAllowed(ReplayState.PAUSED))
        assertFalse(lifecycle.isAllowed(ReplayState.INITIAL))
    }

    @Test
    fun `test transitions from CLOSED state`() {
        val lifecycle = ReplayLifecycle()
        lifecycle.currentState = ReplayState.CLOSED

        assertFalse(lifecycle.isAllowed(ReplayState.INITIAL))
        assertFalse(lifecycle.isAllowed(ReplayState.STARTED))
        assertFalse(lifecycle.isAllowed(ReplayState.RESUMED))
        assertFalse(lifecycle.isAllowed(ReplayState.PAUSED))
        assertFalse(lifecycle.isAllowed(ReplayState.STOPPED))
        assertFalse(lifecycle.isAllowed(ReplayState.CLOSED))
    }

    @Test
    fun `test touch recording is allowed only in STARTED and RESUMED states`() {
        val lifecycle = ReplayLifecycle()

        // Initial state doesn't allow touch recording
        assertFalse(lifecycle.isTouchRecordingAllowed())

        // STARTED state allows touch recording
        lifecycle.currentState = ReplayState.STARTED
        assertTrue(lifecycle.isTouchRecordingAllowed())

        // RESUMED state allows touch recording
        lifecycle.currentState = ReplayState.RESUMED
        assertTrue(lifecycle.isTouchRecordingAllowed())

        // Other states don't allow touch recording
        val otherStates =
            listOf(
                ReplayState.INITIAL,
                ReplayState.PAUSED,
                ReplayState.STOPPED,
                ReplayState.CLOSED,
            )

        otherStates.forEach { state ->
            lifecycle.currentState = state
            assertFalse(lifecycle.isTouchRecordingAllowed())
        }
    }
}

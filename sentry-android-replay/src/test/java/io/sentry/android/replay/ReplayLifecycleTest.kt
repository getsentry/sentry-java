package io.sentry.android.replay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplayLifecycleTest {
  @Test
  fun `test transitions from INITIAL state`() {
    assertTrue(ReplayLifecycleState.INITIAL.isAllowed(ReplayLifecycleState.STARTED))
    assertTrue(ReplayLifecycleState.INITIAL.isAllowed(ReplayLifecycleState.CLOSED))

    assertFalse(ReplayLifecycleState.INITIAL.isAllowed(ReplayLifecycleState.RESUMED))
    assertFalse(ReplayLifecycleState.INITIAL.isAllowed(ReplayLifecycleState.PAUSED))
    assertFalse(ReplayLifecycleState.INITIAL.isAllowed(ReplayLifecycleState.STOPPED))
  }

  @Test
  fun `test transitions from STARTED state`() {
    assertTrue(ReplayLifecycleState.STARTED.isAllowed(ReplayLifecycleState.PAUSED))
    assertTrue(ReplayLifecycleState.STARTED.isAllowed(ReplayLifecycleState.STOPPED))
    assertTrue(ReplayLifecycleState.STARTED.isAllowed(ReplayLifecycleState.CLOSED))

    assertFalse(ReplayLifecycleState.STARTED.isAllowed(ReplayLifecycleState.RESUMED))
    assertFalse(ReplayLifecycleState.STARTED.isAllowed(ReplayLifecycleState.INITIAL))
  }

  @Test
  fun `test transitions from RESUMED state`() {
    assertTrue(ReplayLifecycleState.RESUMED.isAllowed(ReplayLifecycleState.PAUSED))
    assertTrue(ReplayLifecycleState.RESUMED.isAllowed(ReplayLifecycleState.STOPPED))
    assertTrue(ReplayLifecycleState.RESUMED.isAllowed(ReplayLifecycleState.CLOSED))

    assertFalse(ReplayLifecycleState.RESUMED.isAllowed(ReplayLifecycleState.STARTED))
    assertFalse(ReplayLifecycleState.RESUMED.isAllowed(ReplayLifecycleState.INITIAL))
  }

  @Test
  fun `test transitions from PAUSED state`() {
    assertTrue(ReplayLifecycleState.PAUSED.isAllowed(ReplayLifecycleState.RESUMED))
    assertTrue(ReplayLifecycleState.PAUSED.isAllowed(ReplayLifecycleState.STOPPED))
    assertTrue(ReplayLifecycleState.PAUSED.isAllowed(ReplayLifecycleState.CLOSED))

    assertFalse(ReplayLifecycleState.PAUSED.isAllowed(ReplayLifecycleState.STARTED))
    assertFalse(ReplayLifecycleState.PAUSED.isAllowed(ReplayLifecycleState.INITIAL))
  }

  @Test
  fun `test transitions from STOPPED state`() {
    assertTrue(ReplayLifecycleState.STOPPED.isAllowed(ReplayLifecycleState.STARTED))
    assertTrue(ReplayLifecycleState.STOPPED.isAllowed(ReplayLifecycleState.CLOSED))

    assertFalse(ReplayLifecycleState.STOPPED.isAllowed(ReplayLifecycleState.RESUMED))
    assertFalse(ReplayLifecycleState.STOPPED.isAllowed(ReplayLifecycleState.PAUSED))
    assertFalse(ReplayLifecycleState.STOPPED.isAllowed(ReplayLifecycleState.INITIAL))
  }

  @Test
  fun `test transitions from CLOSED state`() {
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.INITIAL))
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.STARTED))
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.RESUMED))
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.PAUSED))
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.STOPPED))
    assertFalse(ReplayLifecycleState.CLOSED.isAllowed(ReplayLifecycleState.CLOSED))
  }
}

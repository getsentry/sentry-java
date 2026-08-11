package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class SessionTest {

  private fun okSession(): Session = Session(null, null, "environment", "release")

  @Test
  fun `recordNonTerminatingUnhandledError atomically updates an Ok session`() {
    val session = okSession()
    val initialTimestamp = session.timestamp

    val updated = session.recordNonTerminatingUnhandledError()

    assertThat(updated).isTrue()
    assertThat(session.status).isEqualTo(Session.State.Ok)
    assertThat(session.hasNonTerminatingUnhandledError()).isTrue()
    assertThat(session.errorCount()).isEqualTo(1)
    assertThat(session.init).isNull()
    assertThat(session.timestamp).isNotNull()
    assertThat(session.timestamp!!.time).isAtLeast(initialTimestamp!!.time)
    assertThat(session.sequence).isEqualTo(session.timestamp!!.time)
  }

  @Test
  fun `recordNonTerminatingUnhandledError does not change terminal sessions`() {
    for (state in Session.State.entries.filter { it != Session.State.Ok }) {
      val session = okSession()
      session.update(state, null, false)
      val before = session.clone()

      val updated = session.recordNonTerminatingUnhandledError()

      assertThat(updated).isFalse()
      assertThat(session.status).isEqualTo(before.status)
      assertThat(session.hasNonTerminatingUnhandledError())
        .isEqualTo(before.hasNonTerminatingUnhandledError())
      assertThat(session.errorCount()).isEqualTo(before.errorCount())
      assertThat(session.init).isEqualTo(before.init)
      assertThat(session.timestamp).isEqualTo(before.timestamp)
      assertThat(session.sequence).isEqualTo(before.sequence)
    }
  }

  @Test
  fun `end without a non-terminating unhandled error finalizes as Exited`() {
    val session = okSession()

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Exited)
  }

  @Test
  fun `end with a non-terminating unhandled error finalizes as Unhandled`() {
    val session = okSession()
    assertThat(session.hasNonTerminatingUnhandledError()).isFalse()

    session.recordNonTerminatingUnhandledError()
    session.end()

    assertThat(session.status).isEqualTo(Session.State.Unhandled)
    assertThat(session.hasNonTerminatingUnhandledError()).isTrue()
  }

  @Test
  fun `end with a non-terminating unhandled error keeps Abnormal as Abnormal`() {
    val session = okSession()
    session.recordNonTerminatingUnhandledError()
    session.update(Session.State.Abnormal, null, false, "anr")

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Abnormal)
    assertThat(session.hasNonTerminatingUnhandledError()).isTrue()
  }

  @Test
  fun `end with a non-terminating unhandled error keeps Crashed as Crashed`() {
    val session = okSession()
    session.recordNonTerminatingUnhandledError()
    session.update(Session.State.Crashed, null, false)

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Crashed)
    assertThat(session.hasNonTerminatingUnhandledError()).isFalse()
  }

  @Test
  fun `updating to Crashed clears a non-terminating unhandled error and end stays Crashed`() {
    val session = okSession()
    session.recordNonTerminatingUnhandledError()

    session.update(Session.State.Crashed, null, true)
    session.end()

    assertThat(session.status).isEqualTo(Session.State.Crashed)
    assertThat(session.hasNonTerminatingUnhandledError()).isFalse()
  }

  @Test
  fun `clone preserves a non-terminating unhandled error`() {
    val session = okSession()
    session.recordNonTerminatingUnhandledError()

    val clone = session.clone()

    assertThat(clone.hasNonTerminatingUnhandledError()).isTrue()
  }
}

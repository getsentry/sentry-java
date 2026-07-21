package io.sentry

import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import org.mockito.kotlin.mock

class SessionTest {

  private fun okSession(): Session = Session(null, null, "environment", "release")

  @Test
  fun `markPendingUnhandled atomically updates an Ok session`() {
    val session = okSession()
    val initialTimestamp = session.timestamp

    val updated = session.markPendingUnhandled()

    assertThat(updated).isTrue()
    assertThat(session.status).isEqualTo(Session.State.Ok)
    assertThat(session.isPendingUnhandled).isTrue()
    assertThat(session.errorCount()).isEqualTo(1)
    assertThat(session.init).isNull()
    assertThat(session.timestamp).isNotNull()
    assertThat(session.timestamp!!.time).isAtLeast(initialTimestamp!!.time)
    assertThat(session.sequence).isEqualTo(session.timestamp!!.time)
  }

  @Test
  fun `markPendingUnhandled does not change terminal sessions`() {
    for (state in Session.State.entries.filter { it != Session.State.Ok }) {
      val session = okSession()
      session.update(state, null, false)
      val before = session.clone()

      val updated = session.markPendingUnhandled()

      assertThat(updated).isFalse()
      assertThat(session.status).isEqualTo(before.status)
      assertThat(session.isPendingUnhandled).isEqualTo(before.isPendingUnhandled)
      assertThat(session.errorCount()).isEqualTo(before.errorCount())
      assertThat(session.init).isEqualTo(before.init)
      assertThat(session.timestamp).isEqualTo(before.timestamp)
      assertThat(session.sequence).isEqualTo(before.sequence)
    }
  }

  @Test
  fun `end without pending unhandled finalizes as Exited`() {
    val session = okSession()

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Exited)
  }

  @Test
  fun `end with pending unhandled finalizes as Unhandled`() {
    val session = okSession()
    assertThat(session.isPendingUnhandled).isFalse()

    session.setPendingUnhandled(true)
    session.end()

    assertThat(session.status).isEqualTo(Session.State.Unhandled)
    assertThat(session.isPendingUnhandled).isTrue()
  }

  @Test
  fun `end with pending unhandled keeps Abnormal as Abnormal`() {
    val session = okSession()
    session.setPendingUnhandled(true)
    session.update(Session.State.Abnormal, null, false, "anr")

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Abnormal)
    assertThat(session.isPendingUnhandled).isTrue()
  }

  @Test
  fun `end with pending unhandled keeps Crashed as Crashed`() {
    val session = okSession()
    session.setPendingUnhandled(true)
    session.update(Session.State.Crashed, null, false)

    session.end()

    assertThat(session.status).isEqualTo(Session.State.Crashed)
    assertThat(session.isPendingUnhandled).isFalse()
  }

  @Test
  fun `updating to Crashed clears pending unhandled and end stays Crashed`() {
    val session = okSession()
    session.setPendingUnhandled(true)

    session.update(Session.State.Crashed, null, true)
    session.end()

    assertThat(session.status).isEqualTo(Session.State.Crashed)
    assertThat(session.isPendingUnhandled).isFalse()
  }

  @Test
  fun `clone preserves pending unhandled`() {
    val session = okSession()
    session.setPendingUnhandled(true)

    val clone = session.clone()

    assertThat(clone.isPendingUnhandled).isTrue()
  }

  @Test
  fun `serialization round-trips pending unhandled and Unhandled status`() {
    val logger = mock<ILogger>()
    val session = okSession()
    session.setPendingUnhandled(true)
    session.end()
    assertThat(session.status).isEqualTo(Session.State.Unhandled)

    val writer = StringWriter()
    session.serialize(JsonObjectWriter(writer, 100), logger)

    val deserialized =
      Session.Deserializer().deserialize(JsonObjectReader(StringReader(writer.toString())), logger)

    assertThat(deserialized.status).isEqualTo(Session.State.Unhandled)
    assertThat(deserialized.isPendingUnhandled).isTrue()
  }

  @Test
  fun `pending unhandled defaults to false and is not serialized when unset`() {
    val logger = mock<ILogger>()
    val session = okSession()

    val writer = StringWriter()
    session.serialize(JsonObjectWriter(writer, 100), logger)

    assertThat(writer.toString()).doesNotContain("pending_unhandled")
  }
}

package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BuddyRecorderTest {
  @Test
  fun `recording lifecycle returns summary and sentry correlation`() {
    val fixture = Fixture()

    fixture.recorder.start(
      BuddyFlowIntent(
        name = "Checkout",
        developerGoal = "Make checkout observable",
        importance = BuddyFlowImportance.BUSINESS_CRITICAL,
      )
    )
    fixture.clock.advance(1000)
    fixture.recorder.recordScreen("CartActivity")
    fixture.clock.advance(500)
    fixture.recorder.recordStep("submit payment", linkedMapOf("button" to "pay"))
    fixture.clock.advance(500)
    val recording = fixture.recorder.stop()

    assertThat(recording.recording.id).isEqualTo("recording-1")
    assertThat(recording.recording.durationMs).isEqualTo(2000)
    assertThat(recording.summary.screenCount).isEqualTo(1)
    assertThat(recording.summary.stepCount).isEqualTo(1)
    assertThat(recording.summary.timelineItemCount).isEqualTo(4)
    assertThat(recording.timeline.map { it.type })
      .containsExactly(
        BuddyTimelineItem.Type.RECORDING_STARTED,
        BuddyTimelineItem.Type.SCREEN,
        BuddyTimelineItem.Type.STEP,
        BuddyTimelineItem.Type.RECORDING_STOPPED,
      )
      .inOrder()
    assertThat(recording.sentry.traceId).isEqualTo("trace-id")
    assertThat(recording.sentry.spanId).isEqualTo("span-id")
    assertThat(fixture.sentry.setTags).containsEntry("sentry.buddy.recording_id", "recording-1")
    assertThat(fixture.sentry.setTags).containsEntry("sentry.buddy.flow_slug", "checkout")
    assertThat(fixture.sentry.startedTransactionName).isEqualTo("Sentry Buddy Recording: checkout")
    assertThat(fixture.sentry.startedTransactionOperation).isEqualTo("ui.flow_recording")
    assertThat(fixture.sentry.transaction.finished).isTrue()
    assertThat(fixture.sentry.removedTags)
      .containsExactly(
        "sentry.buddy.recording_id",
        "sentry.buddy.flow_slug",
        "sentry.buddy.source",
        "sentry.buddy.use_case",
      )
      .inOrder()
  }

  @Test
  fun `starting while active fails deterministically`() {
    val fixture = Fixture()

    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    assertFailsWith<IllegalStateException> { fixture.recorder.start(BuddyFlowIntent("Login")) }
  }

  @Test
  fun `recording step while inactive fails deterministically`() {
    val fixture = Fixture()

    assertFailsWith<IllegalStateException> { fixture.recorder.recordStep("submit payment") }
  }

  @Test
  fun `screen while inactive is ignored`() {
    val fixture = Fixture()

    fixture.recorder.recordScreen("CheckoutActivity")

    assertThat(fixture.sentry.setTags).isEmpty()
  }

  private class Fixture {
    val clock = FakeClock()
    val sentry = FakeSentryFacade()
    val recorder =
      BuddyRecorder(
        metadataProvider = FakeMetadataProvider(),
        sentryFacade = sentry,
        clock = clock,
        idGenerator = FakeIdGenerator(),
      )
  }

  private class FakeClock : BuddyClock {
    private var nowMs = 0L

    override fun now(): Date = Date(nowMs)

    override fun elapsedRealtimeMillis(): Long = nowMs

    fun advance(ms: Long) {
      nowMs += ms
    }
  }

  private class FakeIdGenerator : BuddyIdGenerator {
    override fun generate(): String = "recording-1"
  }

  private class FakeMetadataProvider : BuddyMetadataProvider {
    override fun appInfo(): BuddyAppInfo =
      BuddyAppInfo(packageName = "com.example", release = "1.0-debug", environment = "debug")

    override fun deviceInfo(): BuddyDeviceInfo = BuddyDeviceInfo(model = "Pixel")
  }

  private class FakeSentryFacade : BuddySentryFacade {
    val setTags = linkedMapOf<String, String>()
    val removedTags = mutableListOf<String>()
    val transaction = FakeTransaction()
    var startedTransactionName: String? = null
    var startedTransactionOperation: String? = null

    override val release: String? = "1.0-debug"
    override val environment: String? = "debug"

    override fun setTag(key: String, value: String) {
      setTags[key] = value
    }

    override fun removeTag(key: String) {
      removedTags += key
    }

    override fun startTransaction(
      name: String,
      operation: String,
      tags: Map<String, String>,
    ): BuddySentryTransaction {
      startedTransactionName = name
      startedTransactionOperation = operation
      transaction.tags.putAll(tags)
      return transaction
    }
  }

  private class FakeTransaction : BuddySentryTransaction {
    val tags = linkedMapOf<String, String>()
    var finished = false

    override val traceId: String? = "trace-id"
    override val spanId: String? = "span-id"

    override fun finish() {
      finished = true
    }
  }
}

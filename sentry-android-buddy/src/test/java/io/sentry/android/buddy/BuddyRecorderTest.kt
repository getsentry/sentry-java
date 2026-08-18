package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.CustomSamplingContext
import io.sentry.Hint
import io.sentry.SamplingContext
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
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
    assertThat(recording.summary.spanCount).isEqualTo(2)
    assertThat(recording.summary.timelineItemCount).isEqualTo(6)
    assertThat(recording.timeline.map { it.type })
      .containsExactly(
        BuddyTimelineItem.Type.RECORDING_STARTED,
        BuddyTimelineItem.Type.SPAN,
        BuddyTimelineItem.Type.SCREEN,
        BuddyTimelineItem.Type.SPAN,
        BuddyTimelineItem.Type.STEP,
        BuddyTimelineItem.Type.RECORDING_STOPPED,
      )
      .inOrder()
    assertThat(recording.timeline.filter { it.type == BuddyTimelineItem.Type.SPAN }.map { it.name })
      .containsExactly("GET /api/items", "db.query")
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

  @Test
  fun `matching transaction records observed spans`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    fixture.recorder.recordTransaction(
      BuddyObservedTransaction(
        recordingId = "recording-1",
        operation = "ui.load",
        transactionName = "SecondActivity",
        spans =
          listOf(
            BuddyObservedSpan(
              id = "external-span",
              timestamp = Date(500),
              operation = "http.client",
              description = "GET /external",
              data = linkedMapOf("op" to "http.client", "span_id" to "external-span"),
            )
          ),
      )
    )

    val recording = fixture.recorder.stop()

    assertThat(recording.summary.spanCount).isEqualTo(3)
    assertThat(recording.timeline.filter { it.type == BuddyTimelineItem.Type.SPAN }.map { it.name })
      .containsExactly("GET /external", "GET /api/items", "db.query")
  }

  @Test
  fun `recording breadcrumb stores useful breadcrumb in timeline`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    fixture.recorder.recordBreadcrumb(
      BuddyObservedBreadcrumb(
        timestamp = Date(500),
        type = "navigation",
        category = "navigation",
        data = linkedMapOf("to" to "/github"),
      )
    )

    val recording = fixture.recorder.stop()

    assertThat(recording.summary.breadcrumbCount).isEqualTo(1)
    val breadcrumb = recording.timeline.first { it.type == BuddyTimelineItem.Type.BREADCRUMB }
    assertThat(breadcrumb.name).isEqualTo("navigation")
    assertThat(breadcrumb.data).containsEntry("to", "/github")
  }

  @Test
  fun `breadcrumb observer records accepted useful breadcrumbs`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.breadcrumbObserver(fixture.recorder, null)
    val breadcrumb =
      Breadcrumb(Date(500)).apply {
        type = "navigation"
        category = "navigation"
        level = SentryLevel.INFO
        setData("to", "/github")
      }

    assertThat(observer.execute(breadcrumb, Hint())).isSameInstanceAs(breadcrumb)
    val recording = fixture.recorder.stop()

    val timelineBreadcrumb =
      recording.timeline.first { it.type == BuddyTimelineItem.Type.BREADCRUMB }
    assertThat(timelineBreadcrumb.data).containsEntry("breadcrumb_type", "navigation")
    assertThat(timelineBreadcrumb.data).containsEntry("category", "navigation")
    assertThat(timelineBreadcrumb.data).containsEntry("level", "INFO")
    assertThat(timelineBreadcrumb.data["data"]).isEqualTo(mapOf("to" to "/github"))
  }

  @Test
  fun `breadcrumb observer ignores non-ui breadcrumbs`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.breadcrumbObserver(fixture.recorder, null)
    val breadcrumb = Breadcrumb(Date(500)).apply { category = "manual" }

    observer.execute(breadcrumb, Hint())
    val recording = fixture.recorder.stop()

    assertThat(recording.summary.breadcrumbCount).isEqualTo(0)
  }

  @Test
  fun `breadcrumb observer respects original callback drops`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val original = SentryOptions.BeforeBreadcrumbCallback { _, _ -> null }
    val observer = RealBuddySentryFacade.breadcrumbObserver(fixture.recorder, original)
    val breadcrumb = Breadcrumb(Date(500)).apply { category = "navigation" }

    assertThat(observer.execute(breadcrumb, Hint())).isNull()
    val recording = fixture.recorder.stop()

    assertThat(recording.summary.breadcrumbCount).isEqualTo(0)
  }

  @Test
  fun `event observer records accepted error events`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.eventObserver(fixture.recorder, null)
    val event =
      SentryEvent(Date(500)).apply {
        level = SentryLevel.ERROR
        transaction = "CheckoutActivity"
        message = Message().apply { formatted = "Checkout failed" }
        exceptions = listOf(SentryException().apply { type = "IllegalStateException" })
      }

    assertThat(observer.execute(event, Hint())).isSameInstanceAs(event)
    val recording = fixture.recorder.stop()

    val timelineEvent = recording.timeline.first { it.type == BuddyTimelineItem.Type.EVENT }
    assertThat(timelineEvent.name).isEqualTo("IllegalStateException")
    assertThat(timelineEvent.data).containsEntry("level", "ERROR")
    assertThat(timelineEvent.data).containsEntry("transaction", "CheckoutActivity")
    assertThat(timelineEvent.data).containsEntry("message", "Checkout failed")
    assertThat(timelineEvent.data).containsEntry("exception_type", "IllegalStateException")
  }

  @Test
  fun `event observer ignores non-error events`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.eventObserver(fixture.recorder, null)
    val event = SentryEvent(Date(500)).apply { level = SentryLevel.INFO }

    observer.execute(event, Hint())
    val recording = fixture.recorder.stop()

    assertThat(recording.timeline.map { it.type }).doesNotContain(BuddyTimelineItem.Type.EVENT)
  }

  @Test
  fun `event observer respects original callback drops`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val original = SentryOptions.BeforeSendCallback { _, _ -> null }
    val observer = RealBuddySentryFacade.eventObserver(fixture.recorder, original)
    val event = SentryEvent(Date(500)).apply { level = SentryLevel.ERROR }

    assertThat(observer.execute(event, Hint())).isNull()
    val recording = fixture.recorder.stop()

    assertThat(recording.timeline.map { it.type }).doesNotContain(BuddyTimelineItem.Type.EVENT)
  }

  @Test
  fun `traces sampler delegates while inactive and samples everything while recording`() {
    val fixture = Fixture()
    val original = SentryOptions.TracesSamplerCallback { 0.25 }
    val sampler = RealBuddySentryFacade.tracesSampler(fixture.recorder, original)

    assertThat(sampler.sample(samplingContext())).isEqualTo(0.25)

    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    assertThat(sampler.sample(samplingContext())).isEqualTo(1.0)
  }

  @Test
  fun `traces sampler returns null while inactive without original sampler`() {
    val fixture = Fixture()
    val sampler = RealBuddySentryFacade.tracesSampler(fixture.recorder, null)

    assertThat(sampler.sample(samplingContext())).isNull()
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

    override val dsn: String? = "https://public@example.com/1"
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
    var madeCurrent = false

    override val traceId: String? = "trace-id"
    override val spanId: String? = "span-id"

    override val spanCount: Int = 2

    override fun makeCurrent() {
      madeCurrent = true
    }

    override fun observedSpans(): List<BuddyObservedSpan> =
      listOf(
        BuddyObservedSpan(
          id = "span-1",
          timestamp = Date(750),
          operation = "http.client",
          description = "GET /api/items",
          data = linkedMapOf("op" to "http.client", "span_id" to "span-1"),
        ),
        BuddyObservedSpan(
          id = "span-2",
          timestamp = Date(1250),
          operation = "db.query",
          description = null,
          data = linkedMapOf("op" to "db.query", "span_id" to "span-2"),
        ),
      )

    override fun finish() {
      finished = true
    }
  }

  private companion object {
    @Suppress("DEPRECATION")
    fun samplingContext(): SamplingContext =
      SamplingContext(TransactionContext("Checkout", "ui.action"), null as CustomSamplingContext?)
  }
}

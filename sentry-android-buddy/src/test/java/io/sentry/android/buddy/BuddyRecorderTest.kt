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
import io.sentry.android.buddy.bridge.*
import io.sentry.android.buddy.model.*
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
  fun `screen while inactive does not start a recording`() {
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
        timestamp = Date(500),
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
  fun `matching navigation transaction records screen`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    fixture.recorder.recordTransaction(
      BuddyObservedTransaction(
        recordingId = "recording-1",
        operation = "navigation",
        transactionName = "/checkout",
        spans = emptyList(),
        timestamp = Date(500),
      )
    )

    val recording = fixture.recorder.stop()

    assertThat(recording.summary.screenCount).isEqualTo(1)
    val screen = recording.timeline.first { it.type == BuddyTimelineItem.Type.SCREEN }
    assertThat(screen.name).isEqualTo("/checkout")
    assertThat(screen.data).containsEntry("source", "sentry_navigation_transaction")
    assertThat(screen.data).containsEntry("transaction", "/checkout")
    assertThat(screen.data).containsEntry("op", "navigation")
  }

  @Test
  fun `recording navigation breadcrumb records screen in timeline`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))

    fixture.recorder.recordBreadcrumb(
      BuddyObservedBreadcrumb(
        timestamp = Date(500),
        type = "navigation",
        category = "navigation",
        data =
          linkedMapOf(
            "from" to "/home",
            "to" to "/github",
            "to_arguments" to mapOf("org" to "sentry"),
          ),
      )
    )

    val recording = fixture.recorder.stop()

    assertThat(recording.summary.screenCount).isEqualTo(1)
    assertThat(recording.summary.breadcrumbCount).isEqualTo(0)
    val screen = recording.timeline.first { it.type == BuddyTimelineItem.Type.SCREEN }
    assertThat(screen.name).isEqualTo("/github")
    assertThat(screen.data).containsEntry("source", "sentry_navigation_breadcrumb")
    assertThat(screen.data).containsEntry("from", "/home")
    assertThat(screen.data).containsEntry("to", "/github")
    assertThat(screen.data).containsEntry("to_argument_keys", listOf("org"))
  }

  @Test
  fun `breadcrumb observer promotes accepted navigation breadcrumbs`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.breadcrumbObserver(fixture.recorder, null)
    val breadcrumb =
      Breadcrumb(Date(500)).apply {
        type = "navigation"
        category = "navigation"
        level = SentryLevel.INFO
        setData("from", "/home")
        setData("to", "/github")
        setData("to_arguments", mapOf("org" to "sentry"))
      }

    assertThat(observer.execute(breadcrumb, Hint())).isSameInstanceAs(breadcrumb)
    val recording = fixture.recorder.stop()

    val screen = recording.timeline.first { it.type == BuddyTimelineItem.Type.SCREEN }
    assertThat(screen.name).isEqualTo("/github")
    assertThat(screen.data).containsEntry("source", "sentry_navigation_breadcrumb")
    assertThat(screen.data).containsEntry("from", "/home")
    assertThat(screen.data).containsEntry("to", "/github")
    assertThat(screen.data).containsEntry("to_argument_keys", listOf("org"))
  }

  @Test
  fun `breadcrumb observer records accepted non-navigation breadcrumbs`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    val observer = RealBuddySentryFacade.breadcrumbObserver(fixture.recorder, null)
    val breadcrumb =
      Breadcrumb(Date(500)).apply {
        type = "http"
        category = "http"
        level = SentryLevel.INFO
        setData("url", "https://example.com")
      }

    assertThat(observer.execute(breadcrumb, Hint())).isSameInstanceAs(breadcrumb)
    val recording = fixture.recorder.stop()

    val timelineBreadcrumb =
      recording.timeline.first { it.type == BuddyTimelineItem.Type.BREADCRUMB }
    assertThat(timelineBreadcrumb.data).containsEntry("breadcrumb_type", "http")
    assertThat(timelineBreadcrumb.data).containsEntry("category", "http")
    assertThat(timelineBreadcrumb.data).containsEntry("level", "INFO")
    assertThat(timelineBreadcrumb.data["data"]).isEqualTo(mapOf("url" to "https://example.com"))
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

  @Test
  fun `live feed records useful passive signals while inactive`() {
    val fixture = Fixture()

    fixture.recorder.recordScreen("CheckoutActivity")
    fixture.recorder.recordEvent(
      BuddyObservedEvent(
        timestamp = Date(500),
        title = "IllegalStateException",
        data = linkedMapOf("message" to "Checkout failed"),
      )
    )

    val feed = fixture.recorder.liveFeedSnapshot()

    assertThat(feed.items.map { it.category })
      .containsExactly(BuddyLiveFeedItem.Category.ERROR, BuddyLiveFeedItem.Category.SCREEN)
      .inOrder()
    assertThat(feed.unviewedAdverseCount).isEqualTo(1)
    assertThat(feed.latestAdverseItem?.timelineItem?.name).isEqualTo("IllegalStateException")
  }

  @Test
  fun `live feed records only adverse passive spans`() {
    val fixture = Fixture()

    fixture.recorder.recordTransaction(
      BuddyObservedTransaction(
        recordingId = null,
        operation = "ui.load",
        transactionName = "CheckoutActivity",
        timestamp = Date(500),
        spans =
          listOf(
            BuddyObservedSpan(
              id = "fast-span",
              timestamp = Date(500),
              operation = "db.query",
              description = "SELECT fast",
              data = linkedMapOf("op" to "db.query", "duration_ms" to 10),
            ),
            BuddyObservedSpan(
              id = "slow-span",
              timestamp = Date(600),
              operation = "http.client",
              description = "GET /slow",
              data = linkedMapOf("op" to "http.client", "duration_ms" to 1200),
            ),
            BuddyObservedSpan(
              id = "failed-span",
              timestamp = Date(700),
              operation = "http.client",
              description = "POST /failed",
              data = linkedMapOf("op" to "http.client", "status" to "INTERNAL_ERROR"),
            ),
          ),
      )
    )

    val feed = fixture.recorder.liveFeedSnapshot()

    assertThat(feed.items.map { it.category })
      .containsExactly(
        BuddyLiveFeedItem.Category.FAILED_SPAN,
        BuddyLiveFeedItem.Category.SLOW_SPAN,
      )
      .inOrder()
    assertThat(feed.unviewedAdverseCount).isEqualTo(2)
    assertThat(feed.latestAdverseItem?.category).isEqualTo(BuddyLiveFeedItem.Category.FAILED_SPAN)
  }

  @Test
  fun `live feed records failed http breadcrumbs`() {
    val fixture = Fixture()

    fixture.recorder.recordBreadcrumb(
      BuddyObservedBreadcrumb(
        timestamp = Date(500),
        type = "http",
        category = "http",
        data =
          linkedMapOf("data" to mapOf("method" to "GET", "url" to "/items", "status_code" to 503)),
      )
    )

    val item = fixture.recorder.liveFeedSnapshot().items.single()

    assertThat(item.category).isEqualTo(BuddyLiveFeedItem.Category.FAILED_HTTP)
    assertThat(item.severity).isEqualTo(Severity.HIGH)
  }

  @Test
  fun `live feed records visible screen for adverse event`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    fixture.recorder.recordScreen("/home")

    fixture.recorder.recordEvent(BuddyObservedEvent(Date(500), "Boom", emptyMap()))

    val item = fixture.recorder.liveFeedSnapshot().latestAdverseItem

    assertThat(item?.visibleScreens).containsExactly("/home")
  }

  @Test
  fun `live feed records screen path for adverse span crossing screens`() {
    val fixture = Fixture()
    fixture.recorder.start(BuddyFlowIntent("Checkout"))
    fixture.recorder.recordScreen("/home")
    fixture.clock.advance(500)
    fixture.recorder.recordScreen("/profile")

    fixture.recorder.recordTransaction(
      BuddyObservedTransaction(
        recordingId = "recording-1",
        operation = "ui.load",
        transactionName = "ProfileActivity",
        timestamp = Date(1000),
        spans =
          listOf(
            BuddyObservedSpan(
              id = "slow-span",
              timestamp = Date(100),
              operation = "ui.load",
              description = "full display",
              data = linkedMapOf("duration_ms" to 1000),
            )
          ),
      )
    )

    val item = fixture.recorder.liveFeedSnapshot().latestAdverseItem

    assertThat(item?.visibleScreens).containsExactly("/home", "/profile").inOrder()
  }

  @Test
  fun `mark live feed seen clears unviewed adverse count`() {
    val fixture = Fixture()
    fixture.recorder.recordEvent(BuddyObservedEvent(Date(500), "Boom", emptyMap()))

    assertThat(fixture.recorder.liveFeedSnapshot().unviewedAdverseCount).isEqualTo(1)

    val feed = fixture.recorder.markLiveFeedSeen()

    assertThat(feed.unviewedAdverseCount).isEqualTo(0)
    assertThat(feed.latestAdverseItem?.viewed).isTrue()
  }

  @Test
  fun `live feed keeps the last 25 useful signals`() {
    val fixture = Fixture()

    repeat(30) { index -> fixture.recorder.recordScreen("Screen $index") }

    val feed = fixture.recorder.liveFeedSnapshot()

    assertThat(feed.items).hasSize(25)
    assertThat(feed.items.first().timelineItem.name).isEqualTo("Screen 29")
    assertThat(feed.items.last().timelineItem.name).isEqualTo("Screen 5")
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

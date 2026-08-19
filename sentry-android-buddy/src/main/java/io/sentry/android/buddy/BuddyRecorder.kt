package io.sentry.android.buddy

import io.sentry.android.buddy.model.BuddyFlowIntent
import io.sentry.android.buddy.model.BuddyFlowRecording
import io.sentry.android.buddy.model.BuddyLiveFeed
import io.sentry.android.buddy.model.BuddyLiveFeedItem
import io.sentry.android.buddy.model.BuddyObservedBreadcrumb
import io.sentry.android.buddy.model.BuddyObservedEvent
import io.sentry.android.buddy.model.BuddyObservedSpan
import io.sentry.android.buddy.model.BuddyObservedTransaction
import io.sentry.android.buddy.model.BuddyRecordingMetadata
import io.sentry.android.buddy.model.BuddyRecordingSummary
import io.sentry.android.buddy.model.BuddySentryCorrelation
import io.sentry.android.buddy.model.BuddyTimelineItem
import io.sentry.android.buddy.model.Severity
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal class BuddyRecorder(
  private val metadataProvider: BuddyMetadataProvider,
  private val sentryFacade: BuddySentryFacade,
  private val clock: BuddyClock = SystemBuddyClock,
  private val idGenerator: BuddyIdGenerator = UuidBuddyIdGenerator,
) {
  private var activeRecording: ActiveRecording? = null
  private val liveFeed = BuddyLiveFeedBuffer(LIVE_FEED_CAPACITY)
  private val liveFeedListeners = mutableListOf<(BuddyLiveFeed) -> Unit>()

  @Synchronized
  fun start(intent: BuddyFlowIntent) {
    check(activeRecording == null) { "A Sentry Buddy recording is already active." }

    val recordingId = idGenerator.generate()
    val tags = buddyTags(recordingId, intent.slug)
    tags.forEach { (key, value) -> sentryFacade.setTag(key, value) }
    val transaction =
      sentryFacade.startTransaction(
        "Sentry Buddy Recording: ${intent.slug}",
        ROOT_TRANSACTION_OP,
        tags,
      )
    val startedAt = clock.now()
    val startElapsedMs = clock.elapsedRealtimeMillis()
    val timeline = mutableListOf<BuddyTimelineItem>()
    timeline +=
      BuddyTimelineItem(
        type = BuddyTimelineItem.Type.RECORDING_STARTED,
        timestamp = startedAt,
        elapsedMs = 0,
        name = intent.name,
      )

    activeRecording =
      ActiveRecording(
        id = recordingId,
        intent = intent,
        startedAt = startedAt,
        startElapsedMs = startElapsedMs,
        tags = tags,
        transaction = transaction,
        timeline = timeline,
      )
  }

  @Synchronized fun isRecording(): Boolean = activeRecording != null

  @Synchronized fun liveFeedSnapshot(): BuddyLiveFeed = liveFeed.snapshot()

  @Synchronized fun markLiveFeedSeen(): BuddyLiveFeed = liveFeed.markAdverseViewed()

  @Synchronized
  fun addLiveFeedListener(listener: (BuddyLiveFeed) -> Unit): () -> Unit {
    liveFeedListeners += listener
    listener(liveFeed.snapshot())
    return { synchronized(this) { liveFeedListeners -= listener } }
  }

  @Synchronized
  fun recordStep(name: String, data: Map<String, Any?> = emptyMap()) {
    val recording = requireActiveRecording()
    val item = timelineItem(BuddyTimelineItem.Type.STEP, name, data, recording)
    recording.timeline += item
    recordLiveFeedItem(item, BuddyLiveFeedItem.Category.STEP, Severity.LOW, adverse = false)
  }

  @Synchronized
  fun recordScreen(name: String) {
    val recording = activeRecording
    val item = screenTimelineItem(name, recording)
    recording?.timeline?.add(item)
    recordLiveFeedItem(item, BuddyLiveFeedItem.Category.SCREEN, Severity.LOW, adverse = false)
  }

  @Synchronized
  fun makeCurrent() {
    activeRecording?.transaction?.makeCurrent()
  }

  @Synchronized
  fun recordTransaction(transaction: BuddyObservedTransaction) {
    val recording = activeRecording
    transaction.spans.forEach { span ->
      span.toLiveFeedItem(recording)?.let { liveFeedItem ->
        recordLiveFeedItem(
          liveFeedItem.item,
          liveFeedItem.category,
          liveFeedItem.severity,
          adverse = true,
        )
      }
    }
    if (
      recording == null ||
        transaction.recordingId != recording.id ||
        transaction.operation == ROOT_TRANSACTION_OP
    ) {
      return
    }
    transaction.toNavigationScreenTimelineItem(recording)?.let { recording.addNavigationScreen(it) }
    recording.addSpanTimelineItems(transaction.spans)
  }

  @Synchronized
  fun recordBreadcrumb(breadcrumb: BuddyObservedBreadcrumb) {
    val recording = activeRecording
    breadcrumb.toNavigationScreenTimelineItem(recording)?.let {
      recording?.addNavigationScreen(it)
      recordLiveFeedItem(it, BuddyLiveFeedItem.Category.SCREEN, Severity.LOW, adverse = false)
      return
    }
    val item = breadcrumb.toTimelineItem(recording)
    recording?.timeline?.add(item)
    breadcrumb.toLiveFeedItem(item)?.let { liveFeedItem ->
      recordLiveFeedItem(
        liveFeedItem.item,
        liveFeedItem.category,
        liveFeedItem.severity,
        adverse = true,
      )
    }
  }

  @Synchronized
  fun recordEvent(event: BuddyObservedEvent) {
    val recording = activeRecording
    val item = event.toTimelineItem(recording)
    recording?.timeline?.add(item)
    recordLiveFeedItem(item, BuddyLiveFeedItem.Category.ERROR, Severity.HIGH, adverse = true)
  }

  @Synchronized
  fun stop(): BuddyFlowRecording {
    val recording = requireActiveRecording()
    val stoppedAt = clock.now()
    val durationMs = elapsedSince(recording)
    recording.addSpanTimelineItems(recording.transaction.observedSpans())
    recording.timeline +=
      BuddyTimelineItem(
        type = BuddyTimelineItem.Type.RECORDING_STOPPED,
        timestamp = stoppedAt,
        elapsedMs = durationMs,
        name = recording.intent.name,
      )

    val result = buildRecording(recording, stoppedAt, durationMs)
    activeRecording = null
    recording.transaction.finish()
    BUDDY_TAG_KEYS.forEach(sentryFacade::removeTag)
    return result
  }

  private fun buildRecording(
    recording: ActiveRecording,
    stoppedAt: Date,
    durationMs: Long,
  ): BuddyFlowRecording {
    val timeline = recording.timeline.sortedWith(compareBy({ it.timestamp }, { it.type.order }))
    return BuddyFlowRecording(
      flow = recording.intent,
      recording =
        BuddyRecordingMetadata(
          id = recording.id,
          source = BuddyRecordingMetadata.MANUAL_DEBUG_RECORDING,
          startedAt = recording.startedAt,
          endedAt = stoppedAt,
          durationMs = durationMs,
        ),
      app = metadataProvider.appInfo(),
      device = metadataProvider.deviceInfo(),
      summary = summary(durationMs, timeline),
      timeline = timeline,
      sentry =
        BuddySentryCorrelation(
          recordingId = recording.id,
          dsn = sentryFacade.dsn,
          traceId = recording.transaction.traceId,
          spanId = recording.transaction.spanId,
          tags = recording.tags,
        ),
    )
  }

  private fun summary(durationMs: Long, timeline: List<BuddyTimelineItem>): BuddyRecordingSummary {
    return BuddyRecordingSummary(
      durationMs = durationMs,
      screenCount = timeline.count { it.type == BuddyTimelineItem.Type.SCREEN },
      spanCount = timeline.count { it.type == BuddyTimelineItem.Type.SPAN },
      breadcrumbCount = timeline.count { it.type == BuddyTimelineItem.Type.BREADCRUMB },
      timelineItemCount = timeline.size,
    )
  }

  private fun ActiveRecording.addSpanTimelineItems(spans: List<BuddyObservedSpan>) {
    spans.forEach { span ->
      if (observedSpanIds.add(span.id)) {
        timeline += span.toTimelineItem(this)
      }
    }
  }

  private fun ActiveRecording.addNavigationScreen(item: BuddyTimelineItem) {
    val destination = item.name ?: return
    val duplicate = observedNavigationScreens.any { screen ->
      screen.destination == destination && abs(screen.elapsedMs - item.elapsedMs) <= 1000
    }
    if (!duplicate) {
      observedNavigationScreens += ObservedNavigationScreen(destination, item.elapsedMs)
      timeline += item
    }
  }

  private fun BuddyObservedTransaction.toNavigationScreenTimelineItem(
    recording: ActiveRecording?
  ): BuddyTimelineItem? {
    if (operation?.lowercase(Locale.ROOT) != NAVIGATION_OP) {
      return null
    }
    val destination = transactionName?.takeIf { it.isNotBlank() } ?: return null
    return BuddyTimelineItem(
      type = BuddyTimelineItem.Type.SCREEN,
      timestamp = timestamp,
      elapsedMs = recording.elapsedAt(timestamp),
      name = destination,
      data =
        linkedMapOf(
          DATA_SOURCE to SOURCE_SENTRY_NAVIGATION_TRANSACTION,
          DATA_TRANSACTION to transactionName,
          DATA_OP to operation,
        ),
    )
  }

  private fun BuddyObservedBreadcrumb.toNavigationScreenTimelineItem(
    recording: ActiveRecording?
  ): BuddyTimelineItem? {
    val normalizedCategory = category?.lowercase(Locale.ROOT)
    val normalizedType = type?.lowercase(Locale.ROOT)
    if (normalizedCategory != NAVIGATION_OP && normalizedType != NAVIGATION_OP) {
      return null
    }

    val navigationData = navigationData()
    val destination = navigationData.stringValue(DATA_TO)?.takeIf { it.isNotBlank() } ?: return null
    val screenData =
      linkedMapOf<String, Any?>(
        DATA_SOURCE to SOURCE_SENTRY_NAVIGATION_BREADCRUMB,
        DATA_FROM to navigationData.stringValue(DATA_FROM),
        DATA_TO to destination,
        DATA_BREADCRUMB_TYPE to type,
        DATA_CATEGORY to category,
      )
    navigationData.argumentKeys(DATA_FROM_ARGUMENTS)?.let {
      screenData[DATA_FROM_ARGUMENT_KEYS] = it
    }
    navigationData.argumentKeys(DATA_TO_ARGUMENTS)?.let { screenData[DATA_TO_ARGUMENT_KEYS] = it }
    data[DATA_HINT]?.let { screenData[DATA_HINT] = it }

    return BuddyTimelineItem(
      type = BuddyTimelineItem.Type.SCREEN,
      timestamp = timestamp,
      elapsedMs = recording.elapsedAt(timestamp),
      name = destination,
      data = screenData.filterValues { it != null },
    )
  }

  private fun BuddyObservedBreadcrumb.navigationData(): Map<*, *> =
    data[DATA_DATA] as? Map<*, *> ?: data

  private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

  private fun Map<*, *>.argumentKeys(key: String): List<String>? {
    val arguments = this[key] as? Map<*, *> ?: return null
    return arguments.keys.mapNotNull { it?.toString() }.sorted().takeIf { it.isNotEmpty() }
  }

  private fun BuddyObservedSpan.toTimelineItem(recording: ActiveRecording?): BuddyTimelineItem =
    BuddyTimelineItem(
      type = BuddyTimelineItem.Type.SPAN,
      timestamp = timestamp,
      elapsedMs = recording.elapsedAt(timestamp),
      name = description ?: operation,
      data = data,
    )

  private fun BuddyObservedBreadcrumb.toTimelineItem(
    recording: ActiveRecording?
  ): BuddyTimelineItem =
    BuddyTimelineItem(
      type = BuddyTimelineItem.Type.BREADCRUMB,
      timestamp = timestamp,
      elapsedMs = recording.elapsedAt(timestamp),
      name = category ?: type,
      data = data,
    )

  private fun BuddyObservedEvent.toTimelineItem(recording: ActiveRecording?): BuddyTimelineItem =
    BuddyTimelineItem(
      type = BuddyTimelineItem.Type.EVENT,
      timestamp = timestamp,
      elapsedMs = recording.elapsedAt(timestamp),
      name = title,
      data = data,
    )

  private fun screenTimelineItem(name: String, recording: ActiveRecording?): BuddyTimelineItem =
    BuddyTimelineItem(
      type = BuddyTimelineItem.Type.SCREEN,
      timestamp = clock.now(),
      elapsedMs = recording?.let { elapsedSince(it) } ?: 0,
      name = name,
    )

  private fun BuddyObservedSpan.toLiveFeedItem(recording: ActiveRecording?): LiveFeedItem? {
    val item = toTimelineItem(recording)
    return item.toAdverseSpanLiveFeedItem()
  }

  private fun BuddyTimelineItem.toAdverseSpanLiveFeedItem(): LiveFeedItem? {
    val status = data.stringValue(DATA_STATUS)
    val isFailed = status != null && status != STATUS_OK
    val isSlow = data.longValue(DATA_DURATION_MS)?.let { it >= SLOW_SPAN_THRESHOLD_MS } == true
    if (!isFailed && !isSlow) {
      return null
    }
    return LiveFeedItem(
      item = this,
      category =
        if (isFailed) BuddyLiveFeedItem.Category.FAILED_SPAN
        else BuddyLiveFeedItem.Category.SLOW_SPAN,
      severity = if (isFailed) Severity.HIGH else Severity.MEDIUM,
    )
  }

  private fun BuddyObservedBreadcrumb.toLiveFeedItem(item: BuddyTimelineItem): LiveFeedItem? {
    val normalizedCategory = category?.lowercase(Locale.ROOT)
    val normalizedType = type?.lowercase(Locale.ROOT)
    if (normalizedCategory != HTTP_OP && normalizedType != HTTP_OP) {
      return null
    }
    val statusCode =
      data.mapValue(DATA_DATA).longValue(DATA_STATUS_CODE) ?: data.longValue(DATA_STATUS_CODE)
    if (statusCode == null || statusCode < FAILED_HTTP_STATUS_CODE) {
      return null
    }
    return LiveFeedItem(
      item = item,
      category = BuddyLiveFeedItem.Category.FAILED_HTTP,
      severity =
        if (statusCode >= SERVER_ERROR_HTTP_STATUS_CODE) Severity.HIGH else Severity.MEDIUM,
    )
  }

  private fun recordLiveFeedItem(
    item: BuddyTimelineItem,
    category: BuddyLiveFeedItem.Category,
    severity: Severity,
    adverse: Boolean,
  ) {
    val snapshot =
      liveFeed.add(
        item = item,
        category = category,
        severity = severity,
        adverse = adverse,
        visibleScreens = activeRecording.visibleScreensFor(item),
      )
    liveFeedListeners.toList().forEach { it(snapshot) }
  }

  private fun ActiveRecording?.visibleScreensFor(item: BuddyTimelineItem): List<String> {
    val recording = this ?: return emptyList()
    val startMs = item.timestamp.time
    val endMs = item.data.longValue(DATA_DURATION_MS)?.let { startMs + it } ?: startMs
    val screenItems =
      recording.timeline
        .filter { it.type == BuddyTimelineItem.Type.SCREEN && !it.name.isNullOrBlank() }
        .sortedBy { it.timestamp.time }
    val screenAtStart = screenItems.lastOrNull { it.timestamp.time <= startMs }
    val screensDuringItem = screenItems.filter { it.timestamp.time in (startMs + 1)..endMs }
    return (listOfNotNull(screenAtStart) + screensDuringItem)
      .mapNotNull { it.name }
      .dedupeConsecutive()
  }

  private fun List<String>.dedupeConsecutive(): List<String> {
    val deduped = mutableListOf<String>()
    forEach { screen ->
      if (deduped.lastOrNull() != screen) {
        deduped += screen
      }
    }
    return deduped
  }

  private fun ActiveRecording?.elapsedAt(timestamp: Date): Long =
    this?.let { timestamp.time - it.startedAt.time } ?: 0

  private fun Map<String, Any?>.mapValue(key: String): Map<*, *> =
    this[key] as? Map<*, *> ?: emptyMap<Any, Any>()

  private fun Map<*, *>.longValue(key: String): Long? =
    when (val value = this[key]) {
      is Number -> value.toLong()
      is String -> value.toLongOrNull()
      else -> null
    }

  private fun timelineItem(
    type: BuddyTimelineItem.Type,
    name: String,
    data: Map<String, Any?>,
    recording: ActiveRecording,
  ): BuddyTimelineItem {
    return BuddyTimelineItem(
      type = type,
      timestamp = clock.now(),
      elapsedMs = elapsedSince(recording),
      name = name,
      data = data,
    )
  }

  private fun elapsedSince(recording: ActiveRecording): Long {
    return clock.elapsedRealtimeMillis() - recording.startElapsedMs
  }

  private fun requireActiveRecording(): ActiveRecording {
    return checkNotNull(activeRecording) { "No Sentry Buddy recording is active." }
  }

  private data class ActiveRecording(
    val id: String,
    val intent: BuddyFlowIntent,
    val startedAt: Date,
    val startElapsedMs: Long,
    val tags: Map<String, String>,
    val transaction: BuddySentryTransaction,
    val timeline: MutableList<BuddyTimelineItem>,
    val observedSpanIds: MutableSet<String> = mutableSetOf(),
    val observedNavigationScreens: MutableList<ObservedNavigationScreen> = mutableListOf(),
  )

  private data class ObservedNavigationScreen(val destination: String, val elapsedMs: Long)

  private data class LiveFeedItem(
    val item: BuddyTimelineItem,
    val category: BuddyLiveFeedItem.Category,
    val severity: Severity,
  )

  private companion object {
    private const val LIVE_FEED_CAPACITY = 25
    private const val SLOW_SPAN_THRESHOLD_MS = 1000L
    private const val FAILED_HTTP_STATUS_CODE = 400L
    private const val SERVER_ERROR_HTTP_STATUS_CODE = 500L
    private const val ROOT_TRANSACTION_OP = "ui.flow_recording"
    private const val HTTP_OP = "http"
    private const val NAVIGATION_OP = "navigation"
    private const val STATUS_OK = "OK"
    private const val TAG_RECORDING_ID = "sentry.buddy.recording_id"
    private const val TAG_FLOW_SLUG = "sentry.buddy.flow_slug"
    private const val TAG_SOURCE = "sentry.buddy.source"
    private const val TAG_USE_CASE = "sentry.buddy.use_case"
    private const val DATA_BREADCRUMB_TYPE = "breadcrumb_type"
    private const val DATA_CATEGORY = "category"
    private const val DATA_DATA = "data"
    private const val DATA_FROM = "from"
    private const val DATA_FROM_ARGUMENT_KEYS = "from_argument_keys"
    private const val DATA_FROM_ARGUMENTS = "from_arguments"
    private const val DATA_HINT = "hint"
    private const val DATA_OP = "op"
    private const val DATA_SOURCE = "source"
    private const val DATA_STATUS = "status"
    private const val DATA_STATUS_CODE = "status_code"
    private const val DATA_DURATION_MS = "duration_ms"
    private const val DATA_TO = "to"
    private const val DATA_TO_ARGUMENT_KEYS = "to_argument_keys"
    private const val DATA_TO_ARGUMENTS = "to_arguments"
    private const val DATA_TRANSACTION = "transaction"
    private const val SOURCE_SENTRY_NAVIGATION_BREADCRUMB = "sentry_navigation_breadcrumb"
    private const val SOURCE_SENTRY_NAVIGATION_TRANSACTION = "sentry_navigation_transaction"
    private val BUDDY_TAG_KEYS = listOf(TAG_RECORDING_ID, TAG_FLOW_SLUG, TAG_SOURCE, TAG_USE_CASE)

    private val BuddyTimelineItem.Type.order: Int
      get() =
        when (this) {
          BuddyTimelineItem.Type.RECORDING_STARTED -> 0
          BuddyTimelineItem.Type.SCREEN -> 1
          BuddyTimelineItem.Type.STEP -> 2
          BuddyTimelineItem.Type.SPAN -> 3
          BuddyTimelineItem.Type.BREADCRUMB -> 4
          BuddyTimelineItem.Type.EVENT -> 5
          BuddyTimelineItem.Type.RECORDING_STOPPED -> 6
        }

    private fun buddyTags(recordingId: String, flowSlug: String): Map<String, String> {
      return linkedMapOf(
        TAG_RECORDING_ID to recordingId,
        TAG_FLOW_SLUG to flowSlug,
        TAG_SOURCE to BuddyRecordingMetadata.MANUAL_DEBUG_RECORDING,
        TAG_USE_CASE to BuddyFlowRecording.USE_CASE,
      )
    }
  }
}

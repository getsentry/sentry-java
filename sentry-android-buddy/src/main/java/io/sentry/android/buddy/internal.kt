package io.sentry.android.buddy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ITransaction
import io.sentry.SamplingContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import java.lang.ref.WeakReference
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
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
    val snapshot = liveFeed.add(item, category, severity, adverse)
    liveFeedListeners.toList().forEach { it(snapshot) }
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

internal class BuddyActivityLifecycleCallbacks(
  private val recorder: BuddyRecorder,
  private var overlayManager: BuddyOverlayManager?,
) : Application.ActivityLifecycleCallbacks {
  private var currentActivity: WeakReference<Activity>? = null

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityResumed(activity: Activity) {
    currentActivity = WeakReference(activity)
    overlayManager?.attach(activity)
    recorder.makeCurrent()
    val screenName = activity.javaClass.simpleName
    recorder.recordScreen(screenName)
    overlayManager?.recordingEvent("Screen: $screenName")
  }

  override fun onActivityPaused(activity: Activity) {
    if (currentActivity?.get() === activity) {
      currentActivity = null
    }
    overlayManager?.detach(activity)
  }

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) {
    if (currentActivity?.get() === activity) {
      currentActivity = null
    }
    overlayManager?.detach(activity)
  }

  fun recordCurrentScreen() {
    currentActivity?.get()?.let {
      val screenName = it.javaClass.simpleName
      recorder.recordScreen(screenName)
      overlayManager?.recordingEvent("Screen: $screenName")
    }
  }

  fun recordingEvent(text: String) {
    overlayManager?.recordingEvent(text)
  }

  fun updateOverlay(options: SentryBuddyOptions) {
    if (!options.showOverlay) {
      overlayManager?.detachAll()
      overlayManager = null
      return
    }
    if (overlayManager == null) {
      overlayManager =
        BuddyOverlayManager(
          SentryBuddySessionController(
            flowAnalysesApi = options.flowAnalysesApi,
            openUrlApi = options.openUrlApi,
          )
        )
    }
    overlayManager?.updateOptions(options)
  }

  fun detachAll() {
    overlayManager?.detachAll()
  }
}

internal class BuddyOverlayManager(private val controller: SentryBuddySessionController) {
  private val overlays = WeakHashMap<Activity, View>()

  fun updateOptions(options: SentryBuddyOptions) {
    controller.sentryUiLinks = options.sentryUiLinks()
  }

  fun attach(activity: Activity) {
    if (overlays.containsKey(activity)) {
      return
    }
    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val hitBounds = BuddyOverlayHitBounds()
    val container = BuddyOverlayContainer(activity, controller, hitBounds)
    container.layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    val composeView = ComposeView(activity)
    composeView.layoutParams =
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      )
    composeView.setContent { MaterialTheme { SentryBuddyInstalledOverlay(controller, hitBounds) } }
    container.addView(composeView)
    try {
      content.addView(container)
      overlays[activity] = container
    } catch (_: IllegalStateException) {
      content.removeView(container)
    }
  }

  fun detach(activity: Activity) {
    val overlay = overlays.remove(activity) ?: return
    (overlay.parent as? ViewGroup)?.removeView(overlay)
  }

  fun detachAll() {
    overlays.keys.toList().forEach(::detach)
  }

  fun recordingEvent(text: String) {
    controller.recordTransientEvent(text)
  }
}

@SuppressLint("ViewConstructor")
internal class BuddyOverlayContainer(
  context: Context,
  private val controller: SentryBuddySessionController,
  private val bubbleHitBounds: BuddyOverlayHitBounds,
) : FrameLayout(context) {
  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val state = controller.state
    if (
      (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) &&
        !bubbleHitBounds.contains(event.x, event.y)
    ) {
      return false
    }
    return super.dispatchTouchEvent(event)
  }
}

internal interface BuddyMetadataProvider {
  fun appInfo(): BuddyAppInfo

  fun deviceInfo(): BuddyDeviceInfo
}

internal class AndroidBuddyMetadataProvider(
  private val application: Application,
  private val sentryFacade: BuddySentryFacade,
) : BuddyMetadataProvider {
  override fun appInfo(): BuddyAppInfo {
    val packageInfo = packageInfo()
    return BuddyAppInfo(
      packageName = application.packageName,
      versionName = packageInfo?.versionName,
      versionCode =
        packageInfo?.let { info ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
          } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
          }
        },
      release = sentryFacade.release,
      environment = sentryFacade.environment,
    )
  }

  override fun deviceInfo(): BuddyDeviceInfo {
    return BuddyDeviceInfo(
      manufacturer = Build.MANUFACTURER,
      model = Build.MODEL,
      osVersion = Build.VERSION.RELEASE,
    )
  }

  @Suppress("DEPRECATION")
  private fun packageInfo(): android.content.pm.PackageInfo? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        application.packageManager.getPackageInfo(
          application.packageName,
          android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        application.packageManager.getPackageInfo(application.packageName, 0)
      }
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
      null
    }
  }
}

internal interface BuddyClock {
  fun now(): Date

  fun elapsedRealtimeMillis(): Long
}

internal object SystemBuddyClock : BuddyClock {
  override fun now(): Date = Date()

  override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

internal interface BuddyIdGenerator {
  fun generate(): String
}

internal object UuidBuddyIdGenerator : BuddyIdGenerator {
  override fun generate(): String = UUID.randomUUID().toString()
}

internal interface BuddySentryFacade {
  val dsn: String?

  val release: String?

  val environment: String?

  fun setTag(key: String, value: String)

  fun removeTag(key: String)

  fun startTransaction(
    name: String,
    operation: String,
    tags: Map<String, String>,
  ): BuddySentryTransaction
}

internal interface BuddySentryTransaction {
  val traceId: String?

  val spanId: String?

  val spanCount: Int

  fun makeCurrent()

  fun observedSpans(): List<BuddyObservedSpan>

  fun finish()
}

internal data class BuddyObservedTransaction(
  val recordingId: String?,
  val operation: String?,
  val transactionName: String?,
  val spans: List<BuddyObservedSpan>,
  val timestamp: Date,
)

internal data class BuddyObservedSpan(
  val id: String,
  val timestamp: Date,
  val operation: String,
  val description: String?,
  val data: Map<String, Any?>,
)

internal data class BuddyObservedBreadcrumb(
  val timestamp: Date,
  val type: String?,
  val category: String?,
  val data: Map<String, Any?>,
)

internal data class BuddyObservedEvent(
  val timestamp: Date,
  val title: String?,
  val data: Map<String, Any?>,
)

internal class RealBuddySentryFacade : BuddySentryFacade {
  override val dsn: String?
    get() = Sentry.getCurrentScopes().options.dsn

  override val release: String?
    get() = Sentry.getCurrentScopes().options.release

  override val environment: String?
    get() = Sentry.getCurrentScopes().options.environment

  override fun setTag(key: String, value: String) {
    Sentry.setTag(key, value)
  }

  override fun removeTag(key: String) {
    Sentry.removeTag(key)
  }

  override fun startTransaction(
    name: String,
    operation: String,
    tags: Map<String, String>,
  ): BuddySentryTransaction {
    val options = TransactionOptions()
    options.setBindToScope(true)
    val transaction =
      Sentry.startTransaction(
        TransactionContext(name, operation, TracesSamplingDecision(true)),
        options,
      )
    tags.forEach { (key, value) -> transaction.setTag(key, value) }
    return RealBuddySentryTransaction(transaction)
  }

  companion object {
    fun eventObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeSendCallback?,
    ): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { event, hint ->
      val processed = original?.execute(event, hint) ?: event.takeIf { original == null }
      processed
        ?.takeIf { it.isUsefulForBuddy() }
        ?.let {
          recorder.recordEvent(it.toBuddyObservedEvent())
        }
      processed
    }

    fun breadcrumbObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeBreadcrumbCallback?,
    ): SentryOptions.BeforeBreadcrumbCallback =
      SentryOptions.BeforeBreadcrumbCallback { breadcrumb, hint ->
        val processed =
          original?.execute(breadcrumb, hint) ?: breadcrumb.takeIf { original == null }
        processed
          ?.takeIf { it.isUsefulForBuddy() }
          ?.let {
            recorder.recordBreadcrumb(it.toBuddyObservedBreadcrumb(hint))
          }
        processed
      }

    fun transactionObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeSendTransactionCallback?,
    ): SentryOptions.BeforeSendTransactionCallback =
      SentryOptions.BeforeSendTransactionCallback { transaction, hint ->
        val processed =
          original?.execute(transaction, hint) ?: transaction.takeIf { original == null }
        processed?.let { recorder.recordTransaction(it.toBuddyObservedTransaction()) }
        processed
      }

    fun tracesSampler(
      recorder: BuddyRecorder,
      original: SentryOptions.TracesSamplerCallback?,
    ): SentryOptions.TracesSamplerCallback =
      SentryOptions.TracesSamplerCallback { samplingContext: SamplingContext ->
        if (recorder.isRecording()) {
          1.0
        } else {
          original?.sample(samplingContext)
        }
      }
  }
}

private fun SentryEvent.isUsefulForBuddy(): Boolean =
  isErrored || level == SentryLevel.ERROR || level == SentryLevel.FATAL

private fun SentryEvent.toBuddyObservedEvent(): BuddyObservedEvent {
  val primaryException = exceptions?.lastOrNull()
  val throwable = throwable
  val title =
    primaryException?.type
      ?: throwable?.javaClass?.name
      ?: message?.formatted
      ?: message?.message
      ?: transaction
      ?: eventId?.toString()

  return BuddyObservedEvent(
    timestamp = timestamp,
    title = title,
    data =
      linkedMapOf<String, Any?>(
          "event_id" to eventId?.toString(),
          "level" to level?.name,
          "transaction" to transaction,
          "message" to (message?.formatted ?: message?.message),
          "logger" to logger,
          "is_crashed" to isCrashed,
          "is_errored" to isErrored,
          "exception_count" to exceptions?.size,
          "exception_type" to primaryException?.type,
          "exception_value" to primaryException?.value,
          "throwable_type" to throwable?.javaClass?.name,
          "throwable_message" to throwable?.message,
          "trace_id" to contexts.trace?.traceId?.toString(),
          "span_id" to contexts.trace?.spanId?.toString(),
          "breadcrumb_count" to breadcrumbs?.size,
        )
        .apply {
          tags?.takeIf { it.isNotEmpty() }?.let { put("tags", it) }
        },
  )
}

private fun Breadcrumb.isUsefulForBuddy(): Boolean {
  val normalizedCategory = category?.lowercase(Locale.ROOT)
  val normalizedType = type?.lowercase(Locale.ROOT)
  return normalizedCategory == "navigation" ||
    normalizedCategory == "http" ||
    normalizedCategory?.startsWith("ui.") == true ||
    normalizedType == "navigation" ||
    normalizedType == "http" ||
    normalizedType == "user"
}

private fun Breadcrumb.toBuddyObservedBreadcrumb(hint: Hint): BuddyObservedBreadcrumb =
  BuddyObservedBreadcrumb(
    timestamp = timestamp,
    type = type,
    category = category,
    data =
      linkedMapOf<String, Any?>(
          "breadcrumb_type" to type,
          "category" to category,
          "message" to message,
          "level" to level?.name,
          "origin" to origin,
        )
        .apply {
          if (data.isNotEmpty()) {
            put("data", data)
          }
          hintSummary(hint)?.let { put("hint", it) }
        },
  )

private fun hintSummary(hint: Hint): String? {
  val knownHints =
    listOf(
      "sentry:typeCheckHint",
      "android:fragment",
      "android:navigationDestination",
      "android:motionEvent",
      "android:view",
    )
  return knownHints.firstOrNull { hint.get(it) != null }
}

internal class RealBuddySentryTransaction(private val transaction: ITransaction) :
  BuddySentryTransaction {
  override val traceId: String?
    get() = transaction.spanContext.traceId.toString()

  override val spanId: String?
    get() = transaction.spanContext.spanId.toString()

  override val spanCount: Int
    get() = transaction.spans.size

  override fun makeCurrent() {
    transaction.makeCurrent()
  }

  override fun observedSpans(): List<BuddyObservedSpan> =
    transaction.spans.map { span ->
      BuddyObservedSpan(
        id = span.spanId.toString(),
        timestamp = Date(TimeUnit.NANOSECONDS.toMillis(span.startDate.nanoTimestamp())),
        operation = span.operation,
        description = span.description,
        data =
          spanData(
            operation = span.operation,
            description = span.description,
            status = span.status?.name,
            origin = span.spanContext.origin,
            traceId = span.traceId.toString(),
            spanId = span.spanId.toString(),
            parentSpanId = span.parentSpanId?.toString(),
            durationMs = span.finishDate?.let { span.startDate.diff(it) / -1_000_000 },
            transactionName = null,
            extraData = span.data,
            tags = span.tags,
          ),
      )
    }

  override fun finish() {
    transaction.finish()
  }
}

private fun SentryTransaction.toBuddyObservedTransaction(): BuddyObservedTransaction {
  val trace = contexts.trace
  return BuddyObservedTransaction(
    recordingId = tags?.get("sentry.buddy.recording_id"),
    operation = trace?.operation,
    transactionName = transaction,
    spans = spans.map { it.toBuddyObservedSpan(transaction) },
    timestamp = Date((startTimestamp * 1000).toLong()),
  )
}

private fun SentrySpan.toBuddyObservedSpan(transactionName: String?): BuddyObservedSpan {
  val durationMs = timestamp?.let { ((it - startTimestamp) * 1000).toLong() }
  return BuddyObservedSpan(
    id = spanId.toString(),
    timestamp = Date((startTimestamp * 1000).toLong()),
    operation = op,
    description = description,
    data =
      spanData(
        operation = op,
        description = description,
        status = status?.name,
        origin = origin,
        traceId = traceId.toString(),
        spanId = spanId.toString(),
        parentSpanId = parentSpanId?.toString(),
        durationMs = durationMs,
        transactionName = transactionName,
        extraData = data,
        tags = tags,
      ),
  )
}

private fun spanData(
  operation: String,
  description: String?,
  status: String?,
  origin: String?,
  traceId: String,
  spanId: String,
  parentSpanId: String?,
  durationMs: Long?,
  transactionName: String?,
  extraData: Map<String, Any?>?,
  tags: Map<String, String>,
): Map<String, Any?> =
  linkedMapOf<String, Any?>(
      "op" to operation,
      "description" to description,
      "status" to status,
      "origin" to origin,
      "trace_id" to traceId,
      "span_id" to spanId,
      "parent_span_id" to parentSpanId,
      "duration_ms" to durationMs,
      "transaction" to transactionName,
    )
    .apply {
      if (!extraData.isNullOrEmpty()) {
        put("data", extraData)
      }
      if (tags.isNotEmpty()) {
        put("tags", tags)
      }
    }

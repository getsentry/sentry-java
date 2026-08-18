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
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.TransactionOptions
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import java.lang.ref.WeakReference
import java.util.Date
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

internal class BuddyRecorder(
  private val metadataProvider: BuddyMetadataProvider,
  private val sentryFacade: BuddySentryFacade,
  private val clock: BuddyClock = SystemBuddyClock,
  private val idGenerator: BuddyIdGenerator = UuidBuddyIdGenerator,
) {
  private var activeRecording: ActiveRecording? = null

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

  @Synchronized
  fun recordStep(name: String, data: Map<String, Any?> = emptyMap()) {
    val recording = requireActiveRecording()
    recording.timeline += timelineItem(BuddyTimelineItem.Type.STEP, name, data, recording)
  }

  @Synchronized
  fun recordScreen(name: String) {
    val recording = activeRecording ?: return
    recording.timeline += timelineItem(BuddyTimelineItem.Type.SCREEN, name, emptyMap(), recording)
  }

  @Synchronized
  fun makeCurrent() {
    activeRecording?.transaction?.makeCurrent()
  }

  @Synchronized
  fun recordTransaction(transaction: BuddyObservedTransaction) {
    val recording = activeRecording ?: return
    if (transaction.recordingId != recording.id || transaction.operation == ROOT_TRANSACTION_OP) {
      return
    }
    recording.addSpanTimelineItems(transaction.spans)
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

  private fun BuddyObservedSpan.toTimelineItem(recording: ActiveRecording): BuddyTimelineItem =
    BuddyTimelineItem(
      type = BuddyTimelineItem.Type.SPAN,
      timestamp = timestamp,
      elapsedMs = timestamp.time - recording.startedAt.time,
      name = description ?: operation,
      data = data,
    )

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
  )

  private companion object {
    private const val ROOT_TRANSACTION_OP = "ui.flow_recording"
    private const val TAG_RECORDING_ID = "sentry.buddy.recording_id"
    private const val TAG_FLOW_SLUG = "sentry.buddy.flow_slug"
    private const val TAG_SOURCE = "sentry.buddy.source"
    private const val TAG_USE_CASE = "sentry.buddy.use_case"
    private val BUDDY_TAG_KEYS = listOf(TAG_RECORDING_ID, TAG_FLOW_SLUG, TAG_SOURCE, TAG_USE_CASE)

    private val BuddyTimelineItem.Type.order: Int
      get() =
        when (this) {
          BuddyTimelineItem.Type.RECORDING_STARTED -> 0
          BuddyTimelineItem.Type.SCREEN -> 1
          BuddyTimelineItem.Type.STEP -> 2
          BuddyTimelineItem.Type.SPAN -> 3
          BuddyTimelineItem.Type.BREADCRUMB -> 4
          BuddyTimelineItem.Type.RECORDING_STOPPED -> 5
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
        BuddyOverlayManager(SentryBuddySessionController(flowAnalysesApi = options.flowAnalysesApi))
    }
  }

  fun detachAll() {
    overlayManager?.detachAll()
  }
}

internal class BuddyOverlayManager(private val controller: SentryBuddySessionController) {
  private val overlays = WeakHashMap<Activity, View>()

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
)

internal data class BuddyObservedSpan(
  val id: String,
  val timestamp: Date,
  val operation: String,
  val description: String?,
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
    val transaction = Sentry.startTransaction(name, operation, options)
    tags.forEach { (key, value) -> transaction.setTag(key, value) }
    return RealBuddySentryTransaction(transaction)
  }

  companion object {
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
  }
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

package io.sentry.android.buddy

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
import io.sentry.TransactionOptions
import java.util.Date
import java.util.UUID
import java.util.WeakHashMap

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
  fun stop(): BuddyFlowRecording {
    val recording = requireActiveRecording()
    val stoppedAt = clock.now()
    val durationMs = elapsedSince(recording)
    recording.timeline +=
      BuddyTimelineItem(
        type = BuddyTimelineItem.Type.RECORDING_STOPPED,
        timestamp = stoppedAt,
        elapsedMs = durationMs,
        name = recording.intent.name,
      )
    activeRecording = null

    val result = buildRecording(recording, stoppedAt, durationMs)
    recording.transaction.finish()
    BUDDY_TAG_KEYS.forEach(sentryFacade::removeTag)
    return result
  }

  private fun buildRecording(
    recording: ActiveRecording,
    stoppedAt: Date,
    durationMs: Long,
  ): BuddyFlowRecording {
    val timeline = recording.timeline.toList()
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
      stepCount = timeline.count { it.type == BuddyTimelineItem.Type.STEP },
      breadcrumbCount = timeline.count { it.type == BuddyTimelineItem.Type.BREADCRUMB },
      timelineItemCount = timeline.size,
    )
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
  )

  private companion object {
    private const val ROOT_TRANSACTION_OP = "ui.flow_recording"
    private const val TAG_RECORDING_ID = "sentry.buddy.recording_id"
    private const val TAG_FLOW_SLUG = "sentry.buddy.flow_slug"
    private const val TAG_SOURCE = "sentry.buddy.source"
    private const val TAG_USE_CASE = "sentry.buddy.use_case"
    private val BUDDY_TAG_KEYS = listOf(TAG_RECORDING_ID, TAG_FLOW_SLUG, TAG_SOURCE, TAG_USE_CASE)

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
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityResumed(activity: Activity) {
    recorder.recordScreen(activity.javaClass.simpleName)
    overlayManager?.attach(activity)
  }

  override fun onActivityPaused(activity: Activity) {
    overlayManager?.detach(activity)
  }

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) {
    overlayManager?.detach(activity)
  }

  fun updateOverlay(options: SentryBuddyOptions) {
    if (!options.showOverlay) {
      overlayManager?.detachAll()
      overlayManager = null
      return
    }
    if (overlayManager == null) {
      overlayManager =
        BuddyOverlayManager(SentryBuddySessionController(analyzer = options.analyzer))
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
    val container = BuddyOverlayContainer(activity, controller)
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
    composeView.setContent { MaterialTheme { SentryBuddyOverlay(controller = controller) {} } }
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
}

internal class BuddyOverlayContainer(
  context: Context,
  private val controller: SentryBuddySessionController,
) : FrameLayout(context) {
  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val state = controller.state
    if (
      (state is SentryBuddySessionState.Closed || state is SentryBuddySessionState.Recording) &&
        !event.isInBubbleTouchBounds(width, density)
    ) {
      return false
    }
    return super.dispatchTouchEvent(event)
  }

  private val density: Float
    get() = resources.displayMetrics.density

  private fun MotionEvent.isInBubbleTouchBounds(width: Int, density: Float): Boolean {
    val touchSize = 120f * density
    return x >= width - touchSize && y <= touchSize
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

  fun finish()
}

internal class RealBuddySentryFacade : BuddySentryFacade {
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
    options.setBindToScope(false)
    val transaction = Sentry.startTransaction(name, operation, options)
    tags.forEach { (key, value) -> transaction.setTag(key, value) }
    return RealBuddySentryTransaction(transaction)
  }
}

internal class RealBuddySentryTransaction(private val transaction: ITransaction) :
  BuddySentryTransaction {
  override val traceId: String?
    get() = transaction.spanContext.traceId.toString()

  override val spanId: String?
    get() = transaction.spanContext.spanId.toString()

  override fun finish() {
    transaction.finish()
  }
}

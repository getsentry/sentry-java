package io.sentry.android.replay.capture

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.IScopes
import io.sentry.SentryLevel.ERROR
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FLUSHED
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_SCREEN_AT_START
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.Companion.createSegment
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.gestures.ReplayGestureConverter
import io.sentry.android.replay.util.ReplayRunnable
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebEvent
import io.sentry.transport.ICurrentDateProvider
import java.io.File
import java.util.Date
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@SuppressLint("UseRequiresApi")
@TargetApi(26)
internal abstract class BaseCaptureStrategy(
  private val options: SentryOptions,
  private val scopes: IScopes?,
  private val dateProvider: ICurrentDateProvider,
  protected val replayExecutor: ScheduledExecutorService,
  protected val persistingExecutor: ScheduledExecutorService,
  private val replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
) : CaptureStrategy {
  internal companion object {
    private const val TAG = "CaptureStrategy"
    // https://github.com/getsentry/sentry-javascript/blob/30eb68fff5077211c30c61ba74625e66ab514870/packages/replay-internal/src/coreHandlers/handleAfterSendEvent.ts#L41
    private const val MAX_CONTEXT_VALUES = 100
  }

  private val gestureConverter = ReplayGestureConverter(dateProvider)

  protected val isTerminating = AtomicBoolean(false)
  protected var cache: ReplayCache? = null
  internal var recorderConfig: ScreenshotRecorderConfig? by
    persistableAtomicNullable(propertyName = "") { _, _, newValue ->
      if (newValue == null) {
        // recorderConfig is only nullable on init, but never after
        return@persistableAtomicNullable
      }
      cache?.persistSegmentValues(SEGMENT_KEY_HEIGHT, newValue.recordingHeight.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_WIDTH, newValue.recordingWidth.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_FRAME_RATE, newValue.frameRate.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_BIT_RATE, newValue.bitRate.toString())
    }
  override var segmentTimestamp by
    persistableAtomicNullable<Date>(propertyName = SEGMENT_KEY_TIMESTAMP) { _, _, newValue ->
      cache?.persistSegmentValues(
        SEGMENT_KEY_TIMESTAMP,
        if (newValue == null) null else DateUtils.getTimestamp(newValue),
      )
    }
  protected val replayStartTimestamp = AtomicLong()
  protected var screenAtStart by
    persistableAtomicNullable<String>(propertyName = SEGMENT_KEY_REPLAY_SCREEN_AT_START)
  override var currentReplayId: SentryId by
    persistableAtomic(initialValue = SentryId.EMPTY_ID, propertyName = SEGMENT_KEY_REPLAY_ID)
  override var currentSegment: Int by
    persistableAtomic(initialValue = -1, propertyName = SEGMENT_KEY_ID)
  override val replayCacheDir: File?
    get() = cache?.replayCacheDir

  override var replayType by persistableAtomic<ReplayType>(propertyName = SEGMENT_KEY_REPLAY_TYPE)
  // Tracks whether the buffer was flushed (segments sent to server). Used by fromDisk()
  // to decide whether to normalize the segment ID to 0 on crash recovery: if never flushed,
  // no segments reached the server, so the recovered segment must be 0.
  override var isFlushed: Boolean by
    persistableAtomic(initialValue = false, propertyName = SEGMENT_KEY_FLUSHED)

  protected val currentEvents: Deque<RRWebEvent> = ConcurrentLinkedDeque()
  private val replayContextLock = Any()
  private val currentTraceIds: MutableSet<String> = linkedSetOf()
  private val currentSegmentNames: MutableSet<String> = linkedSetOf()

  override fun start(segmentId: Int, replayId: SentryId, replayType: ReplayType?) {
    cache = replayCacheProvider?.invoke(replayId) ?: ReplayCache(options, replayId)

    this.currentReplayId = replayId
    this.currentSegment = segmentId
    this.replayType = replayType ?: (if (this is SessionCaptureStrategy) SESSION else BUFFER)

    segmentTimestamp = DateUtils.getCurrentDateTime()
    replayStartTimestamp.set(dateProvider.currentTimeMillis)
  }

  override fun resume() {
    segmentTimestamp = DateUtils.getCurrentDateTime()
  }

  override fun pause() = Unit

  override fun stop() {
    cache?.close()
    replayStartTimestamp.set(0)
    segmentTimestamp = null
    currentReplayId = SentryId.EMPTY_ID
  }

  protected fun createSegmentInternal(
    duration: Long,
    currentSegmentTimestamp: Date,
    replayId: SentryId,
    segmentId: Int,
    height: Int,
    width: Int,
    frameRate: Int,
    bitRate: Int,
    replayType: ReplayType = this.replayType,
    cache: ReplayCache? = this.cache,
    screenAtStart: String? = this.screenAtStart,
    breadcrumbs: List<Breadcrumb>? = null,
    events: Deque<RRWebEvent> = this.currentEvents,
  ): ReplaySegment {
    val (traceIds, segmentNames) =
      synchronized(replayContextLock) {
        val context = currentTraceIds.toList() to currentSegmentNames.toList()
        currentTraceIds.clear()
        currentSegmentNames.clear()
        context
      }
    return createSegment(
      scopes,
      options,
      duration,
      currentSegmentTimestamp,
      replayId,
      segmentId,
      height,
      width,
      replayType,
      cache,
      frameRate,
      bitRate,
      screenAtStart,
      breadcrumbs,
      events,
      traceIds,
      segmentNames,
    )
  }

  override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
    this.recorderConfig = recorderConfig
  }

  override fun onTouchEvent(event: MotionEvent) {
    recorderConfig?.let { config ->
      val rrwebEvents = gestureConverter.convert(event, config)
      if (rrwebEvents != null) {
        currentEvents += rrwebEvents
      }
    }
  }

  override fun registerTraceId(traceId: SentryId) {
    if (traceId != SentryId.EMPTY_ID) {
      synchronized(replayContextLock) {
        if (currentTraceIds.size < MAX_CONTEXT_VALUES) {
          currentTraceIds.add(traceId.toString())
        }
      }
    }
  }

  override fun registerSegmentName(segmentName: String) {
    if (segmentName.isNotEmpty()) {
      synchronized(replayContextLock) {
        if (currentSegmentNames.size < MAX_CONTEXT_VALUES) {
          currentSegmentNames.add(segmentName)
        }
      }
    }
  }

  private inline fun <T> persistableAtomicNullable(
    initialValue: T? = null,
    propertyName: String,
    crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit =
      { _, _, newValue ->
        cache?.persistSegmentValues(propertyName, newValue.toString())
      },
  ): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
      private val value = AtomicReference(initialValue)

      private fun runInBackground(task: () -> Unit) {
        if (options.threadChecker.isMainThread) {
          persistingExecutor.submit(ReplayRunnable("$TAG.runInBackground") { task() })
        } else {
          try {
            task()
          } catch (e: Throwable) {
            options.logger.log(ERROR, "Failed to execute task $TAG.runInBackground", e)
          }
        }
      }

      override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value.get()

      override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        val oldValue = this.value.getAndSet(value)
        if (oldValue != value) {
          runInBackground { onChange(propertyName, oldValue, value) }
        }
      }
    }

  private inline fun <T> persistableAtomic(
    initialValue: T? = null,
    propertyName: String,
    crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit =
      { _, _, newValue ->
        cache?.persistSegmentValues(propertyName, newValue.toString())
      },
  ): ReadWriteProperty<Any?, T> =
    persistableAtomicNullable<T>(initialValue, propertyName, onChange) as ReadWriteProperty<Any?, T>
}

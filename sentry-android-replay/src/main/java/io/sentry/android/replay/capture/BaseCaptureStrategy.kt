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
import io.sentry.android.replay.RecordingMetadata
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_DIST
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ENVIRONMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FLUSHED
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_RELEASE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_SCREEN_AT_START
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_NAME
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_VERSION
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.Companion.createSegment
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.gestures.ReplayGestureConverter
import io.sentry.android.replay.util.ReplayRunnable
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebEvent
import io.sentry.time.AnchoredClock
import io.sentry.time.Deadline
import io.sentry.time.MonotonicTicker
import io.sentry.time.Timestamp
import io.sentry.transport.ICurrentDateProvider
import io.sentry.util.AutoClosableReentrantLock
import java.io.File
import java.util.ArrayDeque
import java.util.Date
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@SuppressLint("UseRequiresApi")
@TargetApi(26)
internal abstract class BaseCaptureStrategy
@Suppress("LongParameterList")
constructor(
  private val options: SentryOptions,
  private val scopes: IScopes?,
  // TODO [v9]: Remove ICurrentDateProvider and anchor on options.epochClock (JAVA-827).
  dateProvider: ICurrentDateProvider,
  private val ticker: MonotonicTicker,
  protected val replayExecutor: ScheduledExecutorService,
  protected val persistingExecutor: ScheduledExecutorService,
  private val replayCacheProvider: ((replayId: SentryId) -> ReplayCache)? = null,
  /** The timeline of the recording this strategy takes over, if it continues one. */
  continuedTimeline: AnchoredClock? = null,
) : CaptureStrategy {
  internal companion object {
    private const val TAG = "CaptureStrategy"
    // https://github.com/getsentry/sentry-javascript/blob/30eb68fff5077211c30c61ba74625e66ab514870/packages/replay-internal/src/coreHandlers/handleAfterSendEvent.ts#L41
    private const val MAX_CONTEXT_VALUES = 100
  }

  protected val clock =
    ReplayClock(
      { Timestamp.ofEpochNanos(DateUtils.millisToNanos(dateProvider.currentTimeMillis)) },
      ticker,
      continuedTimeline,
    )

  /**
   * Makes moving onto a new timeline atomic: checking the wall clock, restarting the clock and
   * draining [currentEvents] for the segment that closes the old timeline happen together. Touch
   * events are stamped and added under it too. So two frames can't both follow one jump, and every
   * touch event stamped on the old timeline is drained while every one stamped on the new one
   * stays.
   */
  private val timelineLock = AutoClosableReentrantLock()

  /**
   * The timeline the work queued on [replayExecutor] is on: the frames it stored and the segment it
   * is building. Only [stampFrame] restarts the clock, and that frame's task moves this onto the
   * new timeline, so the two differ only while such a task is queued.
   */
  @Volatile protected var executorTimeline: AnchoredClock = clock.timeline

  private val gestureConverter = ReplayGestureConverter({ clock.nowMillis() }, ticker)

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
  /**
   * When the recording reaches its `sessionDuration` cap, or `null` while nothing is being
   * recorded. Only [SessionCaptureStrategy] enforces a cap; buffer mode records indefinitely.
   */
  @Volatile protected var replayDeadline: Deadline? = null
  private var recordingMetadata: RecordingMetadata? by
    persistableAtomicNullable(propertyName = "") { _, _, newValue ->
      persistMetadataValue(SEGMENT_KEY_RELEASE, newValue?.release)
      persistMetadataValue(SEGMENT_KEY_ENVIRONMENT, newValue?.environment)
      persistMetadataValue(SEGMENT_KEY_DIST, newValue?.dist)
      persistMetadataValue(SEGMENT_KEY_SDK_NAME, newValue?.sdkVersion?.name)
      persistMetadataValue(SEGMENT_KEY_SDK_VERSION, newValue?.sdkVersion?.version)
    }
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

    segmentTimestamp = DateUtils.getDateTime(clock.nowMillis())
    replayDeadline = Deadline.after(ticker, options.sessionReplay.sessionDuration, MILLISECONDS)
    // the same values MainEventProcessor applies to current-run segments
    recordingMetadata =
      RecordingMetadata(
        release = options.release,
        environment = options.environment,
        dist = options.dist,
        sdkVersion = options.sessionReplay.sdkVersion ?: options.sdkVersion,
      )
  }

  private fun persistMetadataValue(key: String, value: String?) {
    // the segment file stores one key=value per line, so values with line breaks can't be stored
    cache?.persistSegmentValues(key, value?.takeIf { '\n' !in it && '\r' !in it })
  }

  override fun resume() {
    // not re-anchored here: that would make every segment request until the task below runs look
    // like it raced a jump. The first frame after a jump while paused follows it like any other.
    val resumedAt = DateUtils.getDateTime(clock.nowMillis())
    // Keep the timeline update behind queued frames and pause/flush segment creation.
    replayExecutor.submit(ReplayRunnable("$TAG.resume") { segmentTimestamp = resumedAt })
  }

  override fun pause() = Unit

  override fun stop() {
    // Keep cleanup behind queued frames; a later start uses a new capture strategy instance.
    replayExecutor.submit(
      ReplayRunnable("$TAG.stop") {
        cache?.close()
        replayDeadline = null
        segmentTimestamp = null
        currentReplayId = SentryId.EMPTY_ID
      }
    )
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

  /**
   * Stamps a frame captured now. If the wall clock jumped since the previous frame, the recording
   * first moves onto a new timeline anchored on it, unless the jump is forward by more than
   * [maxForwardJumpMillis].
   */
  protected fun stampFrame(maxForwardJumpMillis: Long = Long.MAX_VALUE): StampedFrame =
    timelineLock.acquire().use {
      val jumpMillis = clock.wallClockJumpMillis()
      if (jumpMillis == 0L || jumpMillis > maxForwardJumpMillis) {
        StampedFrame(clock.nowMillis(), jumpedTooFar = jumpMillis > maxForwardJumpMillis)
      } else {
        val oldTimeline = clock.timeline
        val oldTimelineEnd = oldTimeline.nowMillis()
        val newTimeline = clock.restart()
        val oldEvents = ArrayDeque<RRWebEvent>()
        var event = currentEvents.pollFirst()
        while (event != null) {
          oldEvents.add(event)
          event = currentEvents.pollFirst()
        }
        val jump = TimelineJump(oldTimeline, oldTimelineEnd, newTimeline, oldEvents)
        StampedFrame(jump.newTimelineStart, jump)
      }
    }

  override fun onTouchEvent(event: MotionEvent) {
    recorderConfig?.let { config ->
      timelineLock.acquire().use {
        val rrwebEvents = gestureConverter.convert(event, config)
        if (rrwebEvents != null) {
          currentEvents += rrwebEvents
        }
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

  protected class StampedFrame(
    val timestamp: Long,
    /** Set when the recording moved onto a new timeline at this frame. */
    val jump: TimelineJump? = null,
    /** The wall clock jumped forward too far to follow, so the frame stayed on the old timeline. */
    val jumpedTooFar: Boolean = false,
  )

  /**
   * Where the old timeline ended and the new one starts, and the touch events stamped on the old
   * one, which belong to the segment that closes it.
   */
  protected class TimelineJump(
    val oldTimeline: AnchoredClock,
    val oldTimelineEnd: Long,
    val newTimeline: AnchoredClock,
    val oldEvents: Deque<RRWebEvent>,
  ) {
    val newTimelineStart: Long = newTimeline.startTime().epochMillis
  }

  /**
   * The times a segment request reads when it is made, so a busy executor doesn't stretch the
   * segment. If a frame moved the recording onto a new timeline in between, what the request read
   * and the frames its task finds are on different timelines, so the task reads again with [read]
   * on the timeline the queued work is on.
   */
  protected inner class SegmentRequest<T : Any>(private val read: (AnchoredClock) -> T) {
    // null while the task of a frame that followed a jump is still queued: the clock is on the new
    // timeline already, the frames are not
    private val timeline = clock.timeline.takeIf { it === executorTimeline }
    private val requested = timeline?.let(read)

    /** Called from the request's task: the times to use, and the timeline they are on. */
    fun resolve(): Pair<T, AnchoredClock> {
      val current = executorTimeline
      return (requested?.takeIf { timeline === current } ?: read(current)) to current
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

package io.sentry.android.replay.capture

import android.view.MotionEvent
import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent.ReplayType
import io.sentry.SentryReplayEvent.ReplayType.BUFFER
import io.sentry.SentryReplayEvent.ReplayType.SESSION
import io.sentry.android.replay.ReplayCache
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_RECORDING
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_SCREEN_AT_START
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.android.replay.capture.CaptureStrategy.Companion.createSegment
import io.sentry.android.replay.capture.CaptureStrategy.Companion.currentEventsLock
import io.sentry.android.replay.capture.CaptureStrategy.ReplaySegment
import io.sentry.android.replay.util.PersistableLinkedList
import io.sentry.android.replay.util.gracefullyShutdown
import io.sentry.android.replay.util.submitSafely
import io.sentry.protocol.SentryId
import io.sentry.rrweb.RRWebEvent
import io.sentry.rrweb.RRWebIncrementalSnapshotEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebInteractionMoveEvent
import io.sentry.rrweb.RRWebInteractionMoveEvent.Position
import io.sentry.transport.ICurrentDateProvider
import java.io.File
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal abstract class BaseCaptureStrategy(
    private val options: SentryOptions,
    private val hub: IHub?,
    private val dateProvider: ICurrentDateProvider,
    executor: ScheduledExecutorService? = null,
    private val replayCacheProvider: ((replayId: SentryId, recorderConfig: ScreenshotRecorderConfig) -> ReplayCache)? = null
) : CaptureStrategy {

    internal companion object {
        private const val TAG = "CaptureStrategy"

        // rrweb values
        private const val TOUCH_MOVE_DEBOUNCE_THRESHOLD = 50
        private const val CAPTURE_MOVE_EVENT_THRESHOLD = 500
    }

    private val persistingExecutor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor(ReplayPersistingExecutorServiceThreadFactory())
    }

    protected val isTerminating = AtomicBoolean(false)
    protected var cache: ReplayCache? = null
    protected var recorderConfig: ScreenshotRecorderConfig by persistableAtomic { _, _, newValue ->
        if (newValue == null) {
            // recorderConfig is only nullable on init, but never after
            return@persistableAtomic
        }
        cache?.persistSegmentValues(SEGMENT_KEY_HEIGHT, newValue.recordingHeight.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_WIDTH, newValue.recordingWidth.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_FRAME_RATE, newValue.frameRate.toString())
        cache?.persistSegmentValues(SEGMENT_KEY_BIT_RATE, newValue.bitRate.toString())
    }
    protected var segmentTimestamp by persistableAtomicNullable<Date>(propertyName = SEGMENT_KEY_TIMESTAMP) { _, _, newValue ->
        cache?.persistSegmentValues(SEGMENT_KEY_TIMESTAMP, if (newValue == null) null else DateUtils.getTimestamp(newValue))
    }
    protected val replayStartTimestamp = AtomicLong()
    protected var screenAtStart by persistableAtomicNullable<String>(propertyName = SEGMENT_KEY_REPLAY_SCREEN_AT_START)
    override var currentReplayId: SentryId by persistableAtomic(initialValue = SentryId.EMPTY_ID, propertyName = SEGMENT_KEY_REPLAY_ID)
    override var currentSegment: Int by persistableAtomic(initialValue = -1, propertyName = SEGMENT_KEY_ID)
    override val replayCacheDir: File? get() = cache?.replayCacheDir

    override var replayType by persistableAtomic<ReplayType>(propertyName = SEGMENT_KEY_REPLAY_TYPE)
    protected val currentEvents: LinkedList<RRWebEvent> = PersistableLinkedList(
        propertyName = SEGMENT_KEY_REPLAY_RECORDING,
        options,
        persistingExecutor,
        cacheProvider = { cache }
    )
    private val currentPositions = LinkedHashMap<Int, ArrayList<Position>>(10)
    private var touchMoveBaseline = 0L
    private var lastCapturedMoveEvent = 0L

    protected val replayExecutor: ScheduledExecutorService by lazy {
        executor ?: Executors.newSingleThreadScheduledExecutor(ReplayExecutorServiceThreadFactory())
    }

    override fun start(
        recorderConfig: ScreenshotRecorderConfig,
        segmentId: Int,
        replayId: SentryId,
        replayType: ReplayType?
    ) {
        cache = replayCacheProvider?.invoke(replayId, recorderConfig) ?: ReplayCache(options, replayId, recorderConfig)

        this.replayType = replayType ?: (if (this is SessionCaptureStrategy) SESSION else BUFFER)
        this.recorderConfig = recorderConfig
        this.currentSegment = segmentId
        this.currentReplayId = replayId

        segmentTimestamp = DateUtils.getCurrentDateTime()
        replayStartTimestamp.set(dateProvider.currentTimeMillis)
    }

    override fun resume() {
        segmentTimestamp = DateUtils.getCurrentDateTime()
    }

    override fun pause() = Unit

    override fun stop() {
        cache?.close()
        currentSegment = -1
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
        replayType: ReplayType = this.replayType,
        cache: ReplayCache? = this.cache,
        frameRate: Int = recorderConfig.frameRate,
        screenAtStart: String? = this.screenAtStart,
        breadcrumbs: List<Breadcrumb>? = null,
        events: LinkedList<RRWebEvent> = this.currentEvents
    ): ReplaySegment =
        createSegment(
            hub,
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
            screenAtStart,
            breadcrumbs,
            events
        )

    override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
        this.recorderConfig = recorderConfig
    }

    override fun onTouchEvent(event: MotionEvent) {
        val rrwebEvents = event.toRRWebIncrementalSnapshotEvent()
        if (rrwebEvents != null) {
            synchronized(currentEventsLock) {
                currentEvents += rrwebEvents
            }
        }
    }

    override fun close() {
        replayExecutor.gracefullyShutdown(options)
    }

    private class ReplayExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayIntegration-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }

    private class ReplayPersistingExecutorServiceThreadFactory : ThreadFactory {
        private var cnt = 0
        override fun newThread(r: Runnable): Thread {
            val ret = Thread(r, "SentryReplayPersister-" + cnt++)
            ret.setDaemon(true)
            return ret
        }
    }

    private fun MotionEvent.toRRWebIncrementalSnapshotEvent(): List<RRWebIncrementalSnapshotEvent>? {
        val event = this
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // we only throttle move events as those can be overwhelming
                val now = dateProvider.currentTimeMillis
                if (lastCapturedMoveEvent != 0L && lastCapturedMoveEvent + TOUCH_MOVE_DEBOUNCE_THRESHOLD > now) {
                    return null
                }
                lastCapturedMoveEvent = now

                currentPositions.keys.forEach { pId ->
                    val pIndex = event.findPointerIndex(pId)

                    if (pIndex == -1) {
                        // no data for this pointer
                        return@forEach
                    }

                    // idk why but rrweb does it like dis
                    if (touchMoveBaseline == 0L) {
                        touchMoveBaseline = now
                    }

                    currentPositions[pId]!! += Position().apply {
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        timeOffset = now - touchMoveBaseline
                    }
                }

                val totalOffset = now - touchMoveBaseline
                return if (totalOffset > CAPTURE_MOVE_EVENT_THRESHOLD) {
                    val moveEvents = mutableListOf<RRWebInteractionMoveEvent>()
                    for ((pointerId, positions) in currentPositions) {
                        if (positions.isNotEmpty()) {
                            moveEvents += RRWebInteractionMoveEvent().apply {
                                this.timestamp = now
                                this.positions = positions.map { pos ->
                                    pos.timeOffset -= totalOffset
                                    pos
                                }
                                this.pointerId = pointerId
                            }
                            currentPositions[pointerId]!!.clear()
                        }
                    }
                    touchMoveBaseline = 0L
                    moveEvents
                } else {
                    null
                }
            }

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pId = event.getPointerId(event.actionIndex)
                val pIndex = event.findPointerIndex(pId)

                if (pIndex == -1) {
                    // no data for this pointer
                    return null
                }

                // new finger down - add a new pointer for tracking movement
                currentPositions[pId] = ArrayList()
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = pId
                        interactionType = InteractionType.TouchStart
                    }
                )
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val pId = event.getPointerId(event.actionIndex)
                val pIndex = event.findPointerIndex(pId)

                if (pIndex == -1) {
                    // no data for this pointer
                    return null
                }

                // finger lift up - remove the pointer from tracking
                currentPositions.remove(pId)
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.getX(pIndex) * recorderConfig.scaleFactorX
                        y = event.getY(pIndex) * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = pId
                        interactionType = InteractionType.TouchEnd
                    }
                )
            }
            MotionEvent.ACTION_CANCEL -> {
                // gesture cancelled - remove all pointers from tracking
                currentPositions.clear()
                listOf(
                    RRWebInteractionEvent().apply {
                        timestamp = dateProvider.currentTimeMillis
                        x = event.x * recorderConfig.scaleFactorX
                        y = event.y * recorderConfig.scaleFactorY
                        id = 0 // html node id, but we don't have it, so hardcode to 0 to align with FE
                        pointerId = 0 // the pointerId is not used for TouchCancel, so just set it to 0
                        interactionType = InteractionType.TouchCancel
                    }
                )
            }

            else -> null
        }
    }

    private inline fun <T> persistableAtomicNullable(
        initialValue: T? = null,
        propertyName: String,
        crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit = { _, _, newValue ->
            cache?.persistSegmentValues(propertyName, newValue.toString())
        }
    ): ReadWriteProperty<Any?, T?> =
        object : ReadWriteProperty<Any?, T?> {
            private val value = AtomicReference(initialValue)

            private fun runInBackground(task: () -> Unit) {
                if (options.mainThreadChecker.isMainThread) {
                    persistingExecutor.submitSafely(options, "$TAG.runInBackground") {
                        task()
                    }
                } else {
                    task()
                }
            }

            init {
                runInBackground { onChange(propertyName, initialValue, initialValue) }
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
        crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit = { _, _, newValue ->
            cache?.persistSegmentValues(propertyName, newValue.toString())
        }
    ): ReadWriteProperty<Any?, T> =
        persistableAtomicNullable<T>(initialValue, propertyName, onChange) as ReadWriteProperty<Any?, T>

    private inline fun <T> persistableAtomic(
        crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit
    ): ReadWriteProperty<Any?, T> =
        persistableAtomicNullable<T>(null, "", onChange) as ReadWriteProperty<Any?, T>
}

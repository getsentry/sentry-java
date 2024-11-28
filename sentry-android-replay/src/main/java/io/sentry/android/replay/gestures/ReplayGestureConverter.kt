package io.sentry.android.replay.gestures

import android.view.MotionEvent
import io.sentry.android.replay.ScreenshotRecorderConfig
import io.sentry.rrweb.RRWebIncrementalSnapshotEvent
import io.sentry.rrweb.RRWebInteractionEvent
import io.sentry.rrweb.RRWebInteractionEvent.InteractionType
import io.sentry.rrweb.RRWebInteractionMoveEvent
import io.sentry.rrweb.RRWebInteractionMoveEvent.Position
import io.sentry.transport.ICurrentDateProvider

internal class ReplayGestureConverter(
    private val dateProvider: ICurrentDateProvider
) {

    internal companion object {
        // rrweb values
        private const val TOUCH_MOVE_DEBOUNCE_THRESHOLD = 50
        private const val CAPTURE_MOVE_EVENT_THRESHOLD = 500
    }

    private val currentPositions = LinkedHashMap<Int, ArrayList<Position>>(10)
    private var touchMoveBaseline = 0L
    private var lastCapturedMoveEvent = 0L

    fun convert(event: MotionEvent, recorderConfig: ScreenshotRecorderConfig): List<RRWebIncrementalSnapshotEvent>? {
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
}

package io.sentry.android.core.internal.gestures

import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlin.math.abs

internal inline fun <reified T : View> Window.mockDecorView(
    id: Int = View.generateViewId(),
    event: MotionEvent,
    touchWithinBounds: Boolean = true,
    clickable: Boolean = false,
    visible: Boolean = true,
    finalize: (T) -> Unit = {}
): T {
    val view = mockView(id, event, touchWithinBounds, clickable, visible, finalize)
    whenever(decorView).doReturn(view)
    return view
}

internal inline fun <reified T : View> mockView(
    id: Int = View.generateViewId(),
    event: MotionEvent,
    touchWithinBounds: Boolean = true,
    clickable: Boolean = false,
    visible: Boolean = true,
    finalize: (T) -> Unit = {}
): T {
    val coordinates = IntArray(2)
    if (!touchWithinBounds) {
        coordinates[0] = (event.x).toInt() + 10
        coordinates[1] = (event.y).toInt() + 10
    } else {
        coordinates[0] = (event.x).toInt() - 10
        coordinates[1] = (event.y).toInt() - 10
    }
    val mockView: T = mock {
        whenever(it.id).thenReturn(id)
        whenever(it.isClickable).thenReturn(clickable)
        whenever(it.visibility).thenReturn(if (visible) View.VISIBLE else View.GONE)

        whenever(it.getLocationOnScreen(any())).doAnswer {
            val array = it.arguments[0] as IntArray
            array[0] = coordinates[0]
            array[1] = coordinates[1]
            null
        }

        val diffPosX = abs(event.x - coordinates[0]).toInt()
        val diffPosY = abs(event.y - coordinates[1]).toInt()
        whenever(it.width).thenReturn(diffPosX + 10)
        whenever(it.height).thenReturn(diffPosY + 10)

        finalize(this.mock)
    }

    return mockView
}

internal fun Resources.mockForTarget(target: View, expectedResourceName: String) {
    whenever(getResourceEntryName(target.id)).thenReturn(expectedResourceName)
}

package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.Window
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal inline fun <reified T : View> Window.mockDecorView(
  id: Int = View.generateViewId(),
  event: MotionEvent,
  touchWithinBounds: Boolean = true,
  clickable: Boolean = false,
  visible: Boolean = true,
  context: Context? = null,
  finalize: (T) -> Unit = {},
): T {
  val view = mockView(id, event, touchWithinBounds, clickable, visible, context, finalize)
  whenever(peekDecorView()).doReturn(view)
  return view
}

internal inline fun <reified T : View> mockView(
  id: Int = View.generateViewId(),
  event: MotionEvent,
  touchWithinBounds: Boolean = true,
  clickable: Boolean = false,
  visible: Boolean = true,
  context: Context? = null,
  finalize: (T) -> Unit = {},
): T {
  // The decor-view-relative touch point used in these tests is (0, 0), and child views are mocked
  // at offset (0, 0), so the point reaches every view unchanged. A view therefore contains the
  // touch iff its width/height are non-negative; a negative size marks the touch as outside.
  val size = if (touchWithinBounds) 10 else -1
  val mockView: T = mock {
    whenever(it.id).thenReturn(id)
    whenever(it.context).thenReturn(context)
    whenever(it.isClickable).thenReturn(clickable)
    whenever(it.visibility).thenReturn(if (visible) View.VISIBLE else View.GONE)
    whenever(it.width).thenReturn(size)
    whenever(it.height).thenReturn(size)

    finalize(this.mock)
  }

  return mockView
}

internal fun Resources.mockForTarget(target: View, expectedResourceName: String?) {
  if (expectedResourceName == null) {
    whenever(getResourceEntryName(target.id))
      .thenThrow(Resources.NotFoundException("res not found"))
  } else {
    whenever(getResourceEntryName(target.id)).thenReturn(expectedResourceName)
  }
}

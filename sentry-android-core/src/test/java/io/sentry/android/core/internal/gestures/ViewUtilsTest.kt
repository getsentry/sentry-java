package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.internal.gestures.UiElement
import io.sentry.util.LazyEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ViewUtilsTest {
  @Test
  fun `getResourceId returns resourceId when available`() {
    val view =
      mock<View> {
        whenever(it.id).doReturn(View.generateViewId())

        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(resources.getResourceEntryName(it.id)).thenReturn("test_view")
        whenever(context.resources).thenReturn(resources)
        whenever(it.context).thenReturn(context)
      }

    assertEquals(ViewUtils.getResourceId(view), "test_view")
  }

  @Test
  fun `getResourceId throws when resource id is not available`() {
    val view =
      mock<View> {
        whenever(it.id).doReturn(View.generateViewId())

        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(resources.getResourceEntryName(any())).doThrow(Resources.NotFoundException())
        whenever(context.resources).thenReturn(resources)
        whenever(it.context).thenReturn(context)
      }

    assertFailsWith<Resources.NotFoundException> { ViewUtils.getResourceId(view) }
  }

  @Test
  fun `when view has no id set, resource name is not looked up `() {
    val context = mock<Context>()
    val resources = mock<Resources>()
    whenever(context.resources).thenReturn(resources)

    val view =
      mock<View> {
        whenever(it.id).doReturn(View.NO_ID)
        whenever(it.context).thenReturn(context)
      }

    assertFailsWith<Resources.NotFoundException> { ViewUtils.getResourceId(view) }
    verify(context, never()).resources
  }

  @Test
  fun `when view id is generated, resource name is not looked up `() {
    val context = mock<Context>()
    val resources = mock<Resources>()
    whenever(context.resources).thenReturn(resources)

    val view =
      mock<View> {
        // View.generateViewId() starts with 1
        whenever(it.id).doReturn(1)
        whenever(it.context).thenReturn(context)
      }

    assertFailsWith<Resources.NotFoundException> { ViewUtils.getResourceId(view) }
    verify(context, never()).resources
  }

  @Test
  fun `findTarget hit-tests children in their own local coordinate space`() {
    val context = mock<Context>()
    val resources = mock<Resources>()
    whenever(context.resources).thenReturn(resources)
    whenever(resources.getResourceEntryName(any())).thenReturn("child")

    // A clickable child positioned at (100, 200) within the decor view, 50x50 in size.
    val child =
      mock<View> {
        whenever(it.id).thenReturn(0x7f010001)
        whenever(it.context).thenReturn(context)
        whenever(it.isClickable).thenReturn(true)
        whenever(it.visibility).thenReturn(View.VISIBLE)
        whenever(it.left).thenReturn(100)
        whenever(it.top).thenReturn(200)
        whenever(it.width).thenReturn(50)
        whenever(it.height).thenReturn(50)
      }
    val decorView =
      mock<ViewGroup> {
        whenever(it.width).thenReturn(1000)
        whenever(it.height).thenReturn(1000)
        whenever(it.childCount).thenReturn(1)
        whenever(it.getChildAt(0)).thenReturn(child)
      }
    val options =
      SentryAndroidOptions().apply {
        gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(LazyEvaluator { true }))
      }

    // (120, 220) maps to (20, 20) in the child's space -> inside its 50x50 bounds.
    assertNotNull(ViewUtils.findTarget(options, decorView, 120f, 220f, UiElement.Type.CLICKABLE))

    // (90, 220) maps to (-10, 20) in the child's space -> outside, despite being inside the decor.
    assertNull(ViewUtils.findTarget(options, decorView, 90f, 220f, UiElement.Type.CLICKABLE))
  }

  @Test
  fun `getResourceIdWithFallback falls back to hexadecimal id when resource not found`() {
    val view =
      mock<View> {
        whenever(it.id).doReturn(1234)

        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(resources.getResourceEntryName(it.id)).thenThrow(Resources.NotFoundException())
        whenever(context.resources).thenReturn(resources)
        whenever(it.context).thenReturn(context)
      }

    assertEquals(ViewUtils.getResourceIdWithFallback(view), "0x4d2")
  }
}

package io.sentry.android.core.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.internal.gestures.UiElement
import io.sentry.util.LazyEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ViewUtilsTest {
  @Test
  fun `findTarget hit-tests children in their own local coordinate space`() {
    val child = clickableChild()
    val decorView =
      mock<ViewGroup> {
        whenever(it.width).thenReturn(1000)
        whenever(it.height).thenReturn(1000)
        whenever(it.childCount).thenReturn(1)
        whenever(it.getChildAt(0)).thenReturn(child)
      }
    val options = optionsWithViewLocator()

    // (120, 220) maps to (20, 20) in the child's space -> inside its 50x50 bounds.
    assertNotNull(ViewUtils.findTarget(options, decorView, 120f, 220f, UiElement.Type.CLICKABLE))

    // (90, 220) maps to (-10, 20) in the child's space -> outside, despite being inside the decor.
    assertNull(ViewUtils.findTarget(options, decorView, 90f, 220f, UiElement.Type.CLICKABLE))
  }

  @Test
  fun `findTarget accounts for parent scroll when mapping into a child`() {
    val child = clickableChild()
    val decorView =
      mock<ViewGroup> {
        whenever(it.width).thenReturn(1000)
        whenever(it.height).thenReturn(1000)
        whenever(it.scrollX).thenReturn(30)
        whenever(it.scrollY).thenReturn(40)
        whenever(it.childCount).thenReturn(1)
        whenever(it.getChildAt(0)).thenReturn(child)
      }
    val options = optionsWithViewLocator()

    // With scroll (30, 40), (90, 180) maps to (90 + 30 - 100, 180 + 40 - 200) = (20, 20) -> inside.
    assertNotNull(ViewUtils.findTarget(options, decorView, 90f, 180f, UiElement.Type.CLICKABLE))

    // The same point without accounting for scroll would map to (-10, -20) -> outside the child.
    assertNull(ViewUtils.findTarget(options, decorView, 50f, 140f, UiElement.Type.CLICKABLE))
  }

  @Test
  fun `findTarget applies the inverse of a non-identity child matrix`() {
    // The child is visually translated by (40, 40) within its parent, so a parent-space point is
    // mapped back by (-40, -40) to reach the child's own coordinate space.
    val matrix = Matrix().apply { setTranslate(40f, 40f) }
    val child = clickableChild { whenever(it.matrix).thenReturn(matrix) }
    val decorView =
      mock<ViewGroup> {
        whenever(it.width).thenReturn(1000)
        whenever(it.height).thenReturn(1000)
        whenever(it.childCount).thenReturn(1)
        whenever(it.getChildAt(0)).thenReturn(child)
      }
    val options = optionsWithViewLocator()

    // (180, 280) lands at (80, 80) before the matrix (outside 50x50), but the inverse pulls it to
    // (40, 40) -> inside.
    assertNotNull(ViewUtils.findTarget(options, decorView, 180f, 280f, UiElement.Type.CLICKABLE))

    // (130, 230) lands at (30, 30) before the matrix (inside), but the inverse pushes it to
    // (-10, -10) -> outside.
    assertNull(ViewUtils.findTarget(options, decorView, 130f, 230f, UiElement.Type.CLICKABLE))
  }

  // A clickable child positioned at (100, 200) within its parent, 50x50 in size.
  private fun clickableChild(finalize: (View) -> Unit = {}): View {
    val context = mock<Context>()
    val resources = mock<Resources>()
    whenever(context.resources).thenReturn(resources)
    whenever(resources.getResourceEntryName(any())).thenReturn("child")
    return mock {
      whenever(it.id).thenReturn(0x7f010001)
      whenever(it.context).thenReturn(context)
      whenever(it.isClickable).thenReturn(true)
      whenever(it.visibility).thenReturn(View.VISIBLE)
      whenever(it.left).thenReturn(100)
      whenever(it.top).thenReturn(200)
      whenever(it.width).thenReturn(50)
      whenever(it.height).thenReturn(50)
      finalize(this.mock)
    }
  }

  private fun optionsWithViewLocator(): SentryAndroidOptions =
    SentryAndroidOptions().apply {
      gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(LazyEvaluator { true }))
    }

  @Test
  fun `getResourceIdOrNull returns resource name when available`() {
    val view =
      mock<View> {
        whenever(it.id).doReturn(0x7f010001)

        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(resources.getResourceEntryName(it.id)).thenReturn("test_view")
        whenever(context.resources).thenReturn(resources)
        whenever(it.context).thenReturn(context)
      }

    assertEquals("test_view", ViewUtils.getResourceIdOrNull(view))
  }

  @Test
  fun `getResourceIdOrNull returns null without throwing for generated id`() {
    val context = mock<Context>()
    val view =
      mock<View> {
        // View.generateViewId() starts with 1
        whenever(it.id).doReturn(1)
        whenever(it.context).thenReturn(context)
      }

    assertNull(ViewUtils.getResourceIdOrNull(view))
    verify(context, never()).resources
  }

  @Test
  fun `getResourceIdOrNull returns null without throwing when view has no id`() {
    val context = mock<Context>()
    val view =
      mock<View> {
        whenever(it.id).doReturn(View.NO_ID)
        whenever(it.context).thenReturn(context)
      }

    assertNull(ViewUtils.getResourceIdOrNull(view))
    verify(context, never()).resources
  }

  @Test
  fun `getResourceIdOrNull returns null without throwing when resource not found`() {
    val view =
      mock<View> {
        whenever(it.id).doReturn(1234)

        val context = mock<Context>()
        val resources = mock<Resources>()
        whenever(resources.getResourceEntryName(it.id)).thenThrow(Resources.NotFoundException())
        whenever(context.resources).thenReturn(resources)
        whenever(it.context).thenReturn(context)
      }

    assertNull(ViewUtils.getResourceIdOrNull(view))
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

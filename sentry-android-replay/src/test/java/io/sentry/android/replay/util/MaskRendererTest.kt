package io.sentry.android.replay.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.GenericViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class MaskRendererTest {

  @Test
  fun `renderMasks returns empty list for recycled bitmap`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.recycle()

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertTrue(result.isEmpty())
    renderer.close()
  }

  @Test
  fun `renderMasks masks GenericViewHierarchyNode`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(10, 10, 90, 90),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertEquals(1, result.size)
    assertEquals(Rect(10, 10, 90, 90), result[0])
    renderer.close()
  }

  @Test
  fun `renderMasks masks ImageViewHierarchyNode with dominant color`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.RED)

    val imageNode =
      ImageViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, imageNode, null)

    assertEquals(1, result.size)
    renderer.close()
  }

  @Test
  fun `renderMasks masks TextViewHierarchyNode with text color`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)

    val textNode =
      TextViewHierarchyNode(
        layout = null,
        dominantColor = Color.BLUE,
        paddingLeft = 0,
        paddingTop = 0,
        x = 0f,
        y = 0f,
        width = 100,
        height = 50,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 50),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, textNode, null)

    assertEquals(1, result.size)
    renderer.close()
  }

  @Test
  fun `renderMasks skips nodes with shouldMask false`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = false,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertTrue(result.isEmpty())
    renderer.close()
  }

  @Test
  fun `renderMasks skips nodes with zero dimensions`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 0,
        height = 0,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 0, 0),
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertTrue(result.isEmpty())
    renderer.close()
  }

  @Test
  fun `renderMasks skips nodes with null visibleRect`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = null,
      )

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertTrue(result.isEmpty())
    renderer.close()
  }

  @Test
  fun `renderMasks applies scale matrix when provided`() {
    val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )

    val scaleMatrix = Matrix().apply { preScale(0.5f, 0.5f) }

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, scaleMatrix)

    assertEquals(1, result.size)
    renderer.close()
  }

  @Test
  fun `renderMasks traverses child nodes`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)

    val childNode =
      GenericViewHierarchyNode(
        x = 10f,
        y = 10f,
        width = 30,
        height = 30,
        elevation = 0f,
        distance = 1,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(10, 10, 40, 40),
      )

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = false,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )
    rootNode.children = listOf(childNode)

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertEquals(1, result.size)
    assertEquals(Rect(10, 10, 40, 40), result[0])
    renderer.close()
  }

  @Test
  fun `renderMasks masks multiple nodes`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)

    val child1 =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 40,
        height = 40,
        elevation = 0f,
        distance = 1,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(0, 0, 40, 40),
      )

    val child2 =
      GenericViewHierarchyNode(
        x = 50f,
        y = 50f,
        width = 40,
        height = 40,
        elevation = 0f,
        distance = 2,
        shouldMask = true,
        isVisible = true,
        visibleRect = Rect(50, 50, 90, 90),
      )

    val rootNode =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 100,
        height = 100,
        elevation = 0f,
        distance = 0,
        shouldMask = false,
        isVisible = true,
        visibleRect = Rect(0, 0, 100, 100),
      )
    rootNode.children = listOf(child1, child2)

    val renderer = MaskRenderer()
    val result = renderer.renderMasks(bitmap, rootNode, null)

    assertEquals(2, result.size)
    renderer.close()
  }

  @Test
  fun `close recycles internal bitmap`() {
    val renderer = MaskRenderer()
    // Trigger lazy initialization by calling renderMasks
    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    val node =
      GenericViewHierarchyNode(
        x = 0f,
        y = 0f,
        width = 10,
        height = 10,
        elevation = 0f,
        distance = 0,
        shouldMask = false,
        isVisible = true,
        visibleRect = Rect(0, 0, 10, 10),
      )
    renderer.renderMasks(bitmap, node, null)

    renderer.close()
    assertTrue(renderer.singlePixelBitmap.isRecycled)
  }
}

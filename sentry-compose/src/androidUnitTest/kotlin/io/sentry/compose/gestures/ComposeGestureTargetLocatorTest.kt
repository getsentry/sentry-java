@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.sentry.compose.gestures

import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.IntSize
import io.sentry.NoOpLogger
import io.sentry.internal.gestures.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ComposeGestureTargetLocatorTest {

  private val locator = ComposeGestureTargetLocator(NoOpLogger.getInstance())

  /**
   * Maps each child [LayoutCoordinates] to its bounding rect. Used by [FakeRootCoordinates] to
   * return correct bounds when [LayoutCoordinates.localBoundingBoxOf] is called.
   */
  private val coordsBounds = mutableMapOf<LayoutCoordinates, Rect>()

  private lateinit var rootCoordinates: LayoutCoordinates

  @Before
  fun setUp() {
    coordsBounds.clear()
    rootCoordinates = FakeRootCoordinates(1000, 1000, coordsBounds)
    coordsBounds[rootCoordinates] = Rect(0f, 0f, 1000f, 1000f)
  }

  @Test
  fun `returns null for non-Owner root`() {
    val result = locator.locate("not an owner", 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `returns null for null root`() {
    val result = locator.locate(null, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `returns null when no clickable elements`() {
    val root = mockLayoutNode(isPlaced = true, tag = "root", width = 100, height = 100)
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `detects clickable via SemanticsModifier OnClick`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "btn",
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("btn", result!!.tag)
    assertEquals("jetpack_compose", result.origin)
  }

  @Test
  fun `detects scrollable via SemanticsModifier ScrollBy`() {
    val scrollableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "list",
        width = 50,
        height = 50,
        semanticsKeys = listOf("ScrollBy"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(scrollableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.SCROLLABLE)
    assertNotNull(result)
    assertEquals("list", result!!.tag)
  }

  @Test
  fun `detects clickable via ClickableElement modifier`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "btn",
        width = 50,
        height = 50,
        nodeModifierClassName = "androidx.compose.foundation.ClickableElement",
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("btn", result!!.tag)
  }

  @Test
  fun `detects clickable via CombinedClickableElement modifier`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "btn",
        width = 50,
        height = 50,
        nodeModifierClassName = "androidx.compose.foundation.CombinedClickableElement",
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("btn", result!!.tag)
  }

  @Test
  fun `detects scrollable via ScrollingLayoutElement modifier`() {
    val scrollableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "scroll",
        width = 50,
        height = 50,
        nodeModifierClassName = "androidx.compose.foundation.ScrollingLayoutElement",
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(scrollableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.SCROLLABLE)
    assertNotNull(result)
    assertEquals("scroll", result!!.tag)
  }

  @Test
  fun `detects scrollable via ScrollingContainerElement modifier`() {
    val scrollableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "scroll",
        width = 50,
        height = 50,
        nodeModifierClassName = "androidx.compose.foundation.ScrollingContainerElement",
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(scrollableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.SCROLLABLE)
    assertNotNull(result)
    assertEquals("scroll", result!!.tag)
  }

  @Test
  fun `ignores clickable when looking for scrollable`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "btn",
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.SCROLLABLE)
    assertNull(result)
  }

  @Test
  fun `ignores scrollable when looking for clickable`() {
    val scrollableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "list",
        width = 50,
        height = 50,
        semanticsKeys = listOf("ScrollBy"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(scrollableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `skips unplaced nodes`() {
    val unplacedClickable =
      mockLayoutNode(
        isPlaced = false,
        tag = "btn",
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(unplacedClickable),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `skips nodes outside bounds`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "btn",
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
        left = 200f,
        top = 200f,
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 300,
        height = 300,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    // click at (5, 5) is outside the child bounds (200-250, 200-250)
    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `child inherits parent tag`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
      )
    val taggedParent =
      mockLayoutNode(
        isPlaced = true,
        tag = "parent_tag",
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 200,
        height = 200,
        children = listOf(taggedParent),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("parent_tag", result!!.tag)
  }

  @Test
  fun `returns deepest clickable for clicks`() {
    val deepChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "deep_btn",
        width = 20,
        height = 20,
        semanticsKeys = listOf("OnClick"),
      )
    val parentClickable =
      mockLayoutNode(
        isPlaced = true,
        tag = "parent_btn",
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
        children = listOf(deepChild),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(parentClickable),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("deep_btn", result!!.tag)
  }

  @Test
  fun `returns first scrollable immediately`() {
    val deepScrollable =
      mockLayoutNode(
        isPlaced = true,
        tag = "deep_scroll",
        width = 20,
        height = 20,
        semanticsKeys = listOf("ScrollBy"),
      )
    val parentScrollable =
      mockLayoutNode(
        isPlaced = true,
        tag = "parent_scroll",
        width = 50,
        height = 50,
        semanticsKeys = listOf("ScrollBy"),
        children = listOf(deepScrollable),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(parentScrollable),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.SCROLLABLE)
    assertNotNull(result)
    assertEquals("parent_scroll", result!!.tag)
  }

  @Test
  fun `returns null when node has no tag and no parent tag`() {
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 50,
        height = 50,
        semanticsKeys = listOf("OnClick"),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(clickableChild),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNull(result)
  }

  @Test
  fun `finds tagged clickable nested under untagged containers`() {
    // Tree: root(no tag) -> container(no tag) -> container(no tag) -> clickable(tag="deep_btn")
    val clickableChild =
      mockLayoutNode(
        isPlaced = true,
        tag = "deep_btn",
        width = 10,
        height = 10,
        semanticsKeys = listOf("OnClick"),
      )
    val innerContainer =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 30,
        height = 30,
        children = listOf(clickableChild),
      )
    val outerContainer =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 60,
        height = 60,
        children = listOf(innerContainer),
      )
    val root =
      mockLayoutNode(
        isPlaced = true,
        tag = null,
        width = 100,
        height = 100,
        children = listOf(outerContainer),
      )
    val owner = mockOwner(root)

    val result = locator.locate(owner, 5f, 5f, UiElement.Type.CLICKABLE)
    assertNotNull(result)
    assertEquals("deep_btn", result!!.tag)
  }

  // -- helpers --

  private fun mockOwner(rootNode: LayoutNode): Owner {
    // Wire the root node's coordinates to the shared rootCoordinates
    whenever(rootNode.coordinates).thenReturn(rootCoordinates)
    val owner = mock<Owner>()
    whenever(owner.root).thenReturn(rootNode)
    return owner
  }

  private fun mockLayoutNode(
    isPlaced: Boolean,
    tag: String?,
    width: Int,
    height: Int,
    children: List<LayoutNode> = emptyList(),
    semanticsKeys: List<String> = emptyList(),
    nodeModifierClassName: String? = null,
    left: Float = 0f,
    top: Float = 0f,
  ): LayoutNode {
    val node = Mockito.mock(LayoutNode::class.java)
    whenever(node.isPlaced).thenReturn(isPlaced)

    val modifierInfoList = mutableListOf<ModifierInfo>()

    if (tag != null) {
      val tagModifierInfo = mockTestTagModifierInfo(tag)
      if (tagModifierInfo != null) {
        modifierInfoList.add(tagModifierInfo)
      } else {
        modifierInfoList.add(mockSemanticsTagModifierInfo(tag))
      }
    }

    if (semanticsKeys.isNotEmpty()) {
      modifierInfoList.add(mockSemanticsKeysModifierInfo(semanticsKeys))
    }

    if (nodeModifierClassName != null) {
      modifierInfoList.add(mockNodeModifierInfo(nodeModifierClassName))
    }

    whenever(node.getModifierInfo()).thenReturn(modifierInfoList)
    whenever(node.zSortedChildren)
      .thenReturn(mutableVectorOf<LayoutNode>().apply { addAll(children) })

    val coordinates = FakeChildCoordinates(left, top, width.toFloat(), height.toFloat())
    coordsBounds[coordinates] = Rect(left, top, left + width, top + height)
    whenever(node.coordinates).thenReturn(coordinates)

    return node
  }

  /**
   * Fake root [LayoutCoordinates] that avoids Mockito issues with inline classes like [Offset].
   * Implements [localBoundingBoxOf] by looking up child bounds in [coordsBounds], and
   * [localToWindow] as an identity transform.
   */
  private class FakeRootCoordinates(
    private val width: Int,
    private val height: Int,
    private val boundsMap: Map<LayoutCoordinates, Rect>,
  ) : LayoutCoordinates {
    override val size: IntSize
      get() = IntSize(width, height)

    override val isAttached: Boolean
      get() = true

    override val parentLayoutCoordinates: LayoutCoordinates?
      get() = null

    override val parentCoordinates: LayoutCoordinates?
      get() = null

    override val providedAlignmentLines: Set<AlignmentLine>
      get() = emptySet()

    override fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified

    override fun windowToLocal(relativeToWindow: Offset): Offset = relativeToWindow

    override fun localToWindow(relativeToLocal: Offset): Offset = relativeToLocal

    override fun localToRoot(relativeToLocal: Offset): Offset = relativeToLocal

    override fun localPositionOf(
      sourceCoordinates: LayoutCoordinates,
      relativeToSource: Offset,
    ): Offset = relativeToSource

    override fun localBoundingBoxOf(
      sourceCoordinates: LayoutCoordinates,
      clipBounds: Boolean,
    ): Rect = boundsMap[sourceCoordinates] ?: Rect.Zero

    @Deprecated("Deprecated in interface")
    override fun localToScreen(relativeToLocal: Offset): Offset = relativeToLocal

    @Deprecated("Deprecated in interface")
    override fun screenToLocal(relativeToScreen: Offset): Offset = relativeToScreen
  }

  /**
   * Minimal fake [LayoutCoordinates] for child nodes. The actual bounds are resolved via
   * [FakeRootCoordinates.localBoundingBoxOf], so this only needs identity implementations.
   */
  private class FakeChildCoordinates(
    private val left: Float,
    private val top: Float,
    private val width: Float,
    private val height: Float,
  ) : LayoutCoordinates {
    override val size: IntSize
      get() = IntSize(width.toInt(), height.toInt())

    override val isAttached: Boolean
      get() = true

    override val parentLayoutCoordinates: LayoutCoordinates?
      get() = null

    override val parentCoordinates: LayoutCoordinates?
      get() = null

    override val providedAlignmentLines: Set<AlignmentLine>
      get() = emptySet()

    override fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified

    override fun windowToLocal(relativeToWindow: Offset): Offset = relativeToWindow

    override fun localToWindow(relativeToLocal: Offset): Offset = relativeToLocal

    override fun localToRoot(relativeToLocal: Offset): Offset = relativeToLocal

    override fun localPositionOf(
      sourceCoordinates: LayoutCoordinates,
      relativeToSource: Offset,
    ): Offset = relativeToSource

    override fun localBoundingBoxOf(
      sourceCoordinates: LayoutCoordinates,
      clipBounds: Boolean,
    ): Rect = Rect(left, top, left + width, top + height)

    @Deprecated("Deprecated in interface")
    override fun localToScreen(relativeToLocal: Offset): Offset = relativeToLocal

    @Deprecated("Deprecated in interface")
    override fun screenToLocal(relativeToScreen: Offset): Offset = relativeToScreen
  }

  companion object {
    private fun mockTestTagModifierInfo(tag: String): ModifierInfo? {
      return try {
        val clazz = Class.forName("androidx.compose.ui.platform.TestTagElement")
        val constructor = clazz.declaredConstructors.firstOrNull() ?: return null
        constructor.isAccessible = true
        val instance = constructor.newInstance(tag) as Modifier
        val modifierInfo = Mockito.mock(ModifierInfo::class.java)
        whenever(modifierInfo.modifier).thenReturn(instance)
        modifierInfo
      } catch (_: Throwable) {
        null
      }
    }

    private fun mockSemanticsTagModifierInfo(tag: String): ModifierInfo {
      val modifierInfo = Mockito.mock(ModifierInfo::class.java)
      whenever(modifierInfo.modifier)
        .thenReturn(
          object : SemanticsModifier {
            override val semanticsConfiguration: SemanticsConfiguration
              get() {
                val config = SemanticsConfiguration()
                config.set(SemanticsPropertyKey("TestTag") { s: String?, _: String? -> s }, tag)
                return config
              }
          }
        )
      return modifierInfo
    }

    private fun mockSemanticsKeysModifierInfo(keys: List<String>): ModifierInfo {
      val modifierInfo = Mockito.mock(ModifierInfo::class.java)
      whenever(modifierInfo.modifier)
        .thenReturn(
          object : SemanticsModifier {
            override val semanticsConfiguration: SemanticsConfiguration
              get() {
                val config = SemanticsConfiguration()
                for (key in keys) {
                  config.set(SemanticsPropertyKey<Unit>(key) { _, _ -> }, Unit)
                }
                return config
              }
          }
        )
      return modifierInfo
    }

    private fun mockNodeModifierInfo(className: String): ModifierInfo {
      val modifierWithClassName =
        Mockito.mock(
          try {
            Class.forName(className)
          } catch (_: ClassNotFoundException) {
            Modifier::class.java
          }
        ) as Modifier
      val modifierInfo = Mockito.mock(ModifierInfo::class.java)
      whenever(modifierInfo.modifier).thenReturn(modifierWithClassName)
      return modifierInfo
    }
  }
}

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose.viewhierarchy

import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import io.sentry.NoOpLogger
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter
import io.sentry.protocol.ViewHierarchyNode
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ComposeViewHierarchyExporterTest {
    @Test
    fun testComposeViewHierarchyExport() {
        val rootVhNode = ViewHierarchyNode()

        val childA = mockLayoutNode(true, "childA", 10, 20)
        val childB = mockLayoutNode(true, null, 10, 20)
        val childC = mockLayoutNode(false, null, 10, 20)
        val parent = mockLayoutNode(true, "root", 30, 40, listOf(childA, childB, childC))

        val node = mock<Owner>()
        whenever(node.root).thenReturn(parent)

        val exporter: ViewHierarchyExporter =
            ComposeViewHierarchyExporter(NoOpLogger.getInstance())
        exporter.export(rootVhNode, node)

        Assert.assertEquals(1, rootVhNode.children!!.size.toLong())
        val parentVhNode = rootVhNode.children!![0]

        Assert.assertEquals("root", parentVhNode.tag)
        Assert.assertEquals(30.0, parentVhNode.width!!, 0.001)
        Assert.assertEquals(40.0, parentVhNode.height!!, 0.001)

        // ensure not placed elements (childC) are not part of the view hierarchy
        Assert.assertEquals(2, parentVhNode.children!!.size.toLong())

        val childAVhNode = parentVhNode.children!![0]
        Assert.assertEquals("childA", childAVhNode.tag)
        Assert.assertEquals(10.0, childAVhNode.width!!, 0.001)
        Assert.assertEquals(20.0, childAVhNode.height!!, 0.001)
        Assert.assertNull(childAVhNode.children)

        val childBVhNode = parentVhNode.children!![1]
        Assert.assertNull(childBVhNode.tag)
    }

    companion object {
        private fun mockLayoutNode(
            isPlaced: Boolean,
            tag: String?,
            width: Int,
            height: Int,
            children: List<LayoutNode> = emptyList()
        ): LayoutNode {
            val nodeA = Mockito.mock(
                LayoutNode::class.java
            )
            whenever(nodeA.isPlaced).thenReturn(isPlaced)

            val modifierInfo = Mockito.mock(
                ModifierInfo::class.java
            )
            whenever(modifierInfo.modifier)
                .thenReturn(
                    object : SemanticsModifier {
                        override val semanticsConfiguration: SemanticsConfiguration
                            get() {
                                val config = SemanticsConfiguration()
                                config.set(
                                    SemanticsPropertyKey(
                                        "SentryTag"
                                    ) { s: String?, s2: String? -> s },
                                    tag
                                )
                                return config
                            }
                    }
                )
            val modifierInfoList: MutableList<ModifierInfo> = ArrayList()
            modifierInfoList.add(modifierInfo)
            whenever((nodeA.getModifierInfo())).thenReturn(modifierInfoList)

            whenever((nodeA.zSortedChildren))
                .thenReturn(mutableVectorOf<LayoutNode>().apply { addAll(children) })

            val coordinates = Mockito.mock(
                LayoutCoordinates::class.java
            )
            val parentCoordinates = Mockito.mock(
                LayoutCoordinates::class.java
            )
            whenever(coordinates.parentLayoutCoordinates).thenReturn(parentCoordinates)
            whenever(parentCoordinates.localBoundingBoxOf(any(), any()))
                .thenReturn(Rect(0f, 0f, width.toFloat(), height.toFloat()))
            whenever(nodeA.coordinates).thenReturn(coordinates)
            return nodeA
        }
    }
}

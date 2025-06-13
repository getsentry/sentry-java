@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.compose.AsyncImage
import io.sentry.SentryOptions
import io.sentry.android.replay.maskAllImages
import io.sentry.android.replay.maskAllText
import io.sentry.android.replay.sentryReplayMask
import io.sentry.android.replay.sentryReplayUnmask
import io.sentry.android.replay.util.ComposeTextLayout
import io.sentry.android.replay.util.traverse
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.GenericViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ComposeMaskingOptionsTest {

    @Before
    fun setup() {
        System.setProperty("robolectric.areWindowsMarkedVisible", "true")
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
        ComposeMaskingOptionsActivity.textModifierApplier = null
        ComposeMaskingOptionsActivity.textFieldModifierApplier = null
        ComposeMaskingOptionsActivity.containerModifierApplier = null
        ComposeMaskingOptionsActivity.fontSizeApplier = null
    }

    @Test
    fun `when maskAllText is set all Text nodes are masked`() {
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        assertTrue(textNodes.all { it.shouldMask })
        // no fontSize specified - we don't use the text layout
        assertNull(textNodes.first().layout)
    }

    @Test
    fun `when text is laid out nodes use it`() {
        ComposeMaskingOptionsActivity.fontSizeApplier = { 20.sp }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        // the text should be laid out when fontSize is specified
        assertEquals("Random repo", (textNodes.first().layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text)
    }

    @Test
    fun `when text input field is readOnly still masks it`() {
        ComposeMaskingOptionsActivity.textFieldModifierApplier = {
            // newer versions of compose basically do this when a TextField is readOnly
            Modifier.clearAndSetSemantics { editableText = AnnotatedString("Placeholder") }
        }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertTrue(textNodes[1].shouldMask)
    }

    @Test
    fun `when maskAllText is set to false all Text nodes are unmasked`() {
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = false
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        assertTrue(textNodes.none { it.shouldMask })
    }

    @Test
    fun `when maskAllImages is set all Image nodes are masked`() {
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllImages = true
        }

        val imageNodes = activity.get().collectNodesOfType<ImageViewHierarchyNode>(options)
        assertEquals(1, imageNodes.size) // [AsyncImage]
        assertTrue(imageNodes.all { it.shouldMask })
    }

    @Test
    fun `when retrieving the semantics fails, a node should be masked`() {
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val options = SentryOptions()

        Mockito.mockStatic(ComposeViewHierarchyNode.javaClass)
            .use { mock: MockedStatic<ComposeViewHierarchyNode> ->
                mock.`when`<Any> {
                    ComposeViewHierarchyNode.retrieveSemanticsConfiguration(any<LayoutNode>())
                }.thenThrow(RuntimeException())

                val root = activity.get().window.decorView
                val composeView = root.lookupComposeView()
                assertNotNull(composeView)

                val rootNode = GenericViewHierarchyNode(0f, 0f, 0, 0, 1.0f, -1, shouldMask = true)
                ComposeViewHierarchyNode.fromView(composeView, rootNode, options)

                assertEquals(1, rootNode.children?.size)

                rootNode.traverse { node ->
                    assertTrue(node.shouldMask)
                    true
                }
            }
    }

    @Test
    fun `when retrieving the semantics fails, an error is thrown`() {
        val node = mock<LayoutNode>()
        whenever(node.collapsedSemantics).thenThrow(RuntimeException("Compose Runtime Error"))

        assertThrows(RuntimeException::class.java) {
            ComposeViewHierarchyNode.retrieveSemanticsConfiguration(node)
        }
    }

    @Test
    fun `when maskAllImages is set to false all Image nodes are unmasked`() {
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllImages = false
        }

        val imageNodes = activity.get().collectNodesOfType<ImageViewHierarchyNode>(options)
        assertEquals(1, imageNodes.size) // [AsyncImage]
        assertTrue(imageNodes.none { it.shouldMask })
    }

    @Test
    fun `when sentry-mask modifier is set masks the node`() {
        ComposeMaskingOptionsActivity.textModifierApplier = { Modifier.sentryReplayMask() }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = false
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertTrue(it.shouldMask)
            } else {
                assertFalse(it.shouldMask)
            }
        }
    }

    @Test
    fun `when sentry-unmask modifier is set unmasks the node`() {
        ComposeMaskingOptionsActivity.textModifierApplier = { Modifier.sentryReplayUnmask() }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertFalse(it.shouldMask, "Node with text ${(it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text} should not be masked")
            } else {
                assertTrue(it.shouldMask, "Node with text ${(it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text} should be masked")
            }
        }
    }

    @Test
    fun `when view is not visible, does not mask the view`() {
        ComposeMaskingOptionsActivity.textModifierApplier = { Modifier.semantics { invisibleToUser() } }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions().apply {
            sessionReplay.maskAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertFalse(it.shouldMask)
            } else {
                assertTrue(it.shouldMask)
            }
        }
    }

    @Test
    fun `when a container view is unmasked its children are not unmasked`() {
        ComposeMaskingOptionsActivity.containerModifierApplier = { Modifier.sentryReplayUnmask() }
        val activity = buildActivity(ComposeMaskingOptionsActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val options = SentryOptions()

        val allNodes = activity.get().collectNodesOfType<ViewHierarchyNode>(options)
        val imageNodes = allNodes.filterIsInstance<ImageViewHierarchyNode>()
        val textNodes = allNodes.filterIsInstance<TextViewHierarchyNode>()
        val genericNodes = allNodes.filterIsInstance<GenericViewHierarchyNode>()
        assertTrue(imageNodes.all { it.shouldMask })
        assertTrue(textNodes.all { it.shouldMask })
        assertTrue(genericNodes.none { it.shouldMask })
    }

    private inline fun <reified T> Activity.collectNodesOfType(options: SentryOptions): List<T> {
        val root = window.decorView
        val viewHierarchy = ViewHierarchyNode.fromView(root, null, 0, options)
        root.traverse(viewHierarchy, options)

        val nodes = mutableListOf<T>()
        viewHierarchy.traverse {
            if (it is T) {
                nodes += it
            }
            return@traverse true
        }
        return nodes
    }

    private fun View.lookupComposeView(): View? {
        if (this.javaClass.name.contains("AndroidComposeView")) {
            return this
        }
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val composeView = child.lookupComposeView()
                if (composeView != null) {
                    return composeView
                }
            }
        }
        return null
    }
}

private class ComposeMaskingOptionsActivity : ComponentActivity() {

    companion object {
        var textModifierApplier: (() -> Modifier)? = null
        var textFieldModifierApplier: (() -> Modifier)? = null
        var containerModifierApplier: (() -> Modifier)? = null
        var fontSizeApplier: (() -> TextUnit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!

        setContent {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .then(containerModifierApplier?.invoke() ?: Modifier)
            ) {
                AsyncImage(
                    model = Uri.fromFile(File(image.toURI())),
                    contentDescription = null,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text("Random repo", fontSize = fontSizeApplier?.invoke() ?: TextUnit.Unspecified)
                TextField(
                    modifier = textFieldModifierApplier?.invoke() ?: Modifier,
                    value = TextFieldValue("Placeholder"),
                    onValueChange = { _ -> }
                )
                Button(
                    onClick = {},
                    modifier = Modifier
                        .testTag("button_list_repos_async")
                        .padding(top = 32.dp)
                ) {
                    Text("Make Request", modifier = Modifier.then(textModifierApplier?.invoke() ?: Modifier))
                }
            }
        }
    }
}

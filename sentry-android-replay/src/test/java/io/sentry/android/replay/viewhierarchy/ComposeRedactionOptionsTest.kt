package io.sentry.android.replay.viewhierarchy

import android.app.Activity
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.compose.AsyncImage
import io.sentry.SentryOptions
import io.sentry.android.replay.redactAllImages
import io.sentry.android.replay.redactAllText
import io.sentry.android.replay.sentryReplayIgnore
import io.sentry.android.replay.sentryReplayRedact
import io.sentry.android.replay.util.ComposeTextLayout
import io.sentry.android.replay.util.traverse
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.GenericViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ComposeRedactionOptionsTest {

    @Before
    fun setup() {
        System.setProperty("robolectric.areWindowsMarkedVisible", "true")
        ComposeRedactionOptionsActivity.textModifierApplier = null
        ComposeRedactionOptionsActivity.containerModifierApplier = null
    }

    @Test
    fun `when redactAllText is set all Text nodes are redacted`() {
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        assertTrue(textNodes.all { it.shouldRedact })
        // just a sanity check for parsing the tree
        assertEquals("Random repo", (textNodes[1].layout as ComposeTextLayout).layout.layoutInput.text.text)
    }

    @Test
    fun `when redactAllText is set to false all Text nodes are ignored`() {
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = false
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        assertTrue(textNodes.none { it.shouldRedact })
    }

    @Test
    fun `when redactAllImages is set all Image nodes are redacted`() {
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllImages = true
        }

        val imageNodes = activity.get().collectNodesOfType<ImageViewHierarchyNode>(options)
        assertEquals(1, imageNodes.size) // [AsyncImage]
        assertTrue(imageNodes.all { it.shouldRedact })
    }

    @Test
    fun `when redactAllImages is set to false all Image nodes are ignored`() {
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllImages = false
        }

        val imageNodes = activity.get().collectNodesOfType<ImageViewHierarchyNode>(options)
        assertEquals(1, imageNodes.size) // [AsyncImage]
        assertTrue(imageNodes.none { it.shouldRedact })
    }

    @Test
    fun `when sentry-redact modifier is set redacts the node`() {
        ComposeRedactionOptionsActivity.textModifierApplier = { Modifier.sentryReplayRedact() }
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = false
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertTrue(it.shouldRedact)
            } else {
                assertFalse(it.shouldRedact)
            }
        }
    }

    @Test
    fun `when sentry-ignore modifier is set ignores the node`() {
        ComposeRedactionOptionsActivity.textModifierApplier = { Modifier.sentryReplayIgnore() }
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        assertEquals(4, textNodes.size) // [TextField, Text, Button, Activity Title]
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertFalse(it.shouldRedact)
            } else {
                assertTrue(it.shouldRedact)
            }
        }
    }

    @Test
    fun `when view is not visible, does not redact the view`() {
        ComposeRedactionOptionsActivity.textModifierApplier = { Modifier.semantics { invisibleToUser() } }
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions().apply {
            experimental.sessionReplay.redactAllText = true
        }

        val textNodes = activity.get().collectNodesOfType<TextViewHierarchyNode>(options)
        textNodes.forEach {
            if ((it.layout as? ComposeTextLayout)?.layout?.layoutInput?.text?.text == "Make Request") {
                assertFalse(it.shouldRedact)
            } else {
                assertTrue(it.shouldRedact)
            }
        }
    }

    @Test
    fun `when a container view is ignored its children are not ignored`() {
        ComposeRedactionOptionsActivity.containerModifierApplier = { Modifier.sentryReplayIgnore() }
        val activity = buildActivity(ComposeRedactionOptionsActivity::class.java).setup()

        val options = SentryOptions()

        val allNodes = activity.get().collectNodesOfType<ViewHierarchyNode>(options)
        val imageNodes = allNodes.filterIsInstance<ImageViewHierarchyNode>()
        val textNodes = allNodes.filterIsInstance<TextViewHierarchyNode>()
        val genericNodes = allNodes.filterIsInstance<GenericViewHierarchyNode>()
        assertTrue(imageNodes.all { it.shouldRedact })
        assertTrue(textNodes.all { it.shouldRedact })
        assertTrue(genericNodes.none { it.shouldRedact })
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
}

private class ComposeRedactionOptionsActivity : ComponentActivity() {

    companion object {
        var textModifierApplier: (() -> Modifier)? = null
        var containerModifierApplier: (() -> Modifier)? = null
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
                TextField(
                    value = TextFieldValue("Placeholder"),
                    onValueChange = { _ -> }
                )
                Text("Random repo")
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

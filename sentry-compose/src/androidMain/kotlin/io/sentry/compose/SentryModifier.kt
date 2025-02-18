package io.sentry.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

public object SentryModifier {

    public const val TAG: String = "SentryTag"

    // Based on TestTag
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsProperties.kt;l=166;drc=76bc6975d1b520c545b6f8786ff5c9f0bc22bd1f
    private val SentryTag = SemanticsPropertyKey<String>(
        name = TAG,
        mergePolicy = { parentValue, _ ->
            // Never merge SentryTags, to avoid leaking internal test tags to parents.
            parentValue
        }
    )

    @JvmStatic
    public fun Modifier.sentryTag(tag: String): Modifier =
        this then SentryTagModifierNodeElement(tag)

    private data class SentryTagModifierNodeElement(val tag: String) :
        ModifierNodeElement<SentryTagModifierNode>(), SemanticsModifier {

        override val semanticsConfiguration: SemanticsConfiguration =
            SemanticsConfiguration().also {
                it[SentryTag] = tag
            }

        override fun create(): SentryTagModifierNode = SentryTagModifierNode(tag)

        override fun update(node: SentryTagModifierNode) {
            node.tag = tag
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "sentryTag"
            properties["tag"] = tag
        }
    }

    private class SentryTagModifierNode(var tag: String) :
        Modifier.Node(),
        SemanticsModifierNode {

        override fun SemanticsPropertyReceiver.applySemantics() {
            this[SentryTag] = tag
        }
    }
}

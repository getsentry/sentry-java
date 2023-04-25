package io.sentry.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

// Based on TestTag
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsProperties.kt;l=166;drc=76bc6975d1b520c545b6f8786ff5c9f0bc22bd1f
private val SentryTag = SemanticsPropertyKey<String>(
    name = "SentryTag",
    mergePolicy = { parentValue, _ ->
        // Never merge SentryTags, to avoid leaking internal test tags to parents.
        parentValue
    }
)

public object SentryModifier {

    @JvmStatic
    public fun Modifier.sentryModifier(tag: String): Modifier {
        return semantics(
            properties = {
                this[SentryTag] = tag
            }
        )
    }
}

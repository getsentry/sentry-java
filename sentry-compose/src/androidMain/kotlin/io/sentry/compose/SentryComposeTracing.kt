package io.sentry.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.modifier.ProvidableModifierLocal
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.platform.testTag
import io.sentry.ISpan
import io.sentry.Sentry

@Stable
private class StableHolder<T>(val item: T) {
    operator fun component1(): T = item
}

private val localSentrySpanModifier = ProvidableModifierLocal {
    StableHolder(Sentry.getSpan())
}

@ExperimentalComposeUiApi
public fun Modifier.sentryTraced(tag: String): Modifier = Modifier.composed {
    val span = remember { mutableStateOf<StableHolder<ISpan?>>(StableHolder(null)) }
    this
        .testTag(tag)
        .modifierLocalConsumer {
            span.value =
                StableHolder(localSentrySpanModifier.current.item?.startChild("ui.load", tag))
        }
        .drawWithContent {
            drawContent()
            span.value.apply {
                if (item?.isFinished == false) {
                    item.finish()
                }
            }
        }
        .modifierLocalProvider(localSentrySpanModifier) {
            span.value
        }
}

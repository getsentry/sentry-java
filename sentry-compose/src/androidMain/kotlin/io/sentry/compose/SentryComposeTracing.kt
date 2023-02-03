package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import io.sentry.Sentry
import io.sentry.SpanOptions

private const val OP_PARENT_COMPOSITION = "compose.composition"
private const val OP_COMPOSE = "compose"

private const val OP_PARENT_RENDER = "compose.rendering"
private const val OP_RENDER = "render"

@Immutable
private class ImmutableHolder<T>(var item: T)

private val localSentryCompositionParentSpan = compositionLocalOf {
    ImmutableHolder(
        Sentry.getRootSpan()
            ?.startChild(OP_PARENT_COMPOSITION, null, SpanOptions(true, true, true))
    )
}

private val localSentryRenderingParentSpan = compositionLocalOf {
    ImmutableHolder(
        Sentry.getRootSpan()
            ?.startChild(OP_PARENT_RENDER, null, SpanOptions(true, true, true))
    )
}

@ExperimentalComposeUiApi
@Composable
public fun SentryTraced(
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val parentCompositionSpan = localSentryCompositionParentSpan.current
    val parentRenderingSpan = localSentryRenderingParentSpan.current
    val compositionSpan = parentCompositionSpan.item?.startChild(OP_COMPOSE, tag)
    val firstRendered = remember { ImmutableHolder(false) }

    Box(
        modifier = modifier
            .testTag(tag)
            .drawWithContent {
                val renderSpan = if (!firstRendered.item) {
                    parentRenderingSpan.item?.startChild(
                        OP_RENDER,
                        tag
                    )
                } else {
                    null
                }
                drawContent()
                firstRendered.item = true
                renderSpan?.finish()
            }
    ) {
        content()
    }
    compositionSpan?.finish()
}

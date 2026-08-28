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
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SpanOptions
import io.sentry.compose.SentryModifier.sentryTag

private const val OP_PARENT_COMPOSITION = "ui.compose.composition"
private const val OP_COMPOSE = "ui.compose"

private const val OP_PARENT_RENDER = "ui.compose.rendering"
private const val OP_RENDER = "ui.render"

private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"

@Immutable private class ImmutableHolder<T>(var item: T)

private class ParentSpanHolder {
  private var rootSpan: ISpan? = null
  private var span: ISpan? = null

  fun getOrCreate(rootSpan: ISpan?, operation: String, description: String): ISpan? {
    if (rootSpan == null) {
      this.rootSpan = null
      span = null
      return null
    }

    if (this.rootSpan !== rootSpan || span?.isFinished != false) {
      this.rootSpan = rootSpan
      span =
        rootSpan
          .startChild(
            operation,
            description,
            SpanOptions().apply {
              isTrimStart = true
              isTrimEnd = true
              isIdle = true
            },
          )
          .apply { spanContext.origin = OP_TRACE_ORIGIN }
    }

    return span
  }
}

private fun getRootSpan(): ISpan? = Sentry.getCurrentScopes().transaction

// CompositionLocal defaults are shared process-wide, so cached spans must be validated against
// the root transaction active at the point of use.
private val localSentryCompositionParentSpan = compositionLocalOf { ParentSpanHolder() }

private val localSentryRenderingParentSpan = compositionLocalOf { ParentSpanHolder() }

@ExperimentalComposeUiApi
@Composable
public fun SentryTraced(
  tag: String,
  modifier: Modifier = Modifier,
  enableUserInteractionTracing: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val parentCompositionSpan = localSentryCompositionParentSpan.current
  val parentRenderingSpan = localSentryRenderingParentSpan.current
  val compositionSpan =
    parentCompositionSpan
      .getOrCreate(getRootSpan(), OP_PARENT_COMPOSITION, "Jetpack Compose Initial Composition")
      ?.startChild(OP_COMPOSE, tag)
      ?.apply {
        spanContext.origin = OP_TRACE_ORIGIN
      }
  val firstRendered = remember { ImmutableHolder(false) }

  val baseModifier = if (enableUserInteractionTracing) modifier.sentryTag(tag) else modifier

  Box(
    modifier =
      baseModifier.drawWithContent {
        val renderSpan =
          if (!firstRendered.item) {
            parentRenderingSpan
              .getOrCreate(getRootSpan(), OP_PARENT_RENDER, "Jetpack Compose Initial Render")
              ?.startChild(OP_RENDER, tag)
              ?.apply { spanContext.origin = OP_TRACE_ORIGIN }
          } else {
            null
          }
        drawContent()
        firstRendered.item = true
        renderSpan?.finish()
      },
    propagateMinConstraints = true,
  ) {
    content()
  }
  compositionSpan?.finish()
}

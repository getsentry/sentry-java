package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryDate
import io.sentry.SpanOptions
import io.sentry.compose.SentryModifier.sentryTag

private const val OP_PARENT_COMPOSITION = "ui.compose.composition"
private const val OP_COMPOSE = "ui.compose"

private const val OP_PARENT_RENDER = "ui.compose.rendering"
private const val OP_RENDER = "ui.render"

private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"

private val localSentryCompositionParentSpan = compositionLocalOf {
  ImmutableHolder(
    getRootSpan()
      ?.startChild(
        OP_PARENT_COMPOSITION,
        "Jetpack Compose Initial Composition",
        SpanOptions().apply {
          isTrimStart = true
          isTrimEnd = true
          isIdle = true
        },
      )
      ?.apply { spanContext.origin = OP_TRACE_ORIGIN }
  )
}

private val localSentryRenderingParentSpan = compositionLocalOf {
  ImmutableHolder(
    getRootSpan()
      ?.startChild(
        OP_PARENT_RENDER,
        "Jetpack Compose Initial Render",
        SpanOptions().apply {
          isTrimStart = true
          isTrimEnd = true
          isIdle = true
        },
      )
      ?.apply { spanContext.origin = OP_TRACE_ORIGIN }
  )
}

@Immutable internal class ImmutableHolder<T>(var item: T)

/**
 * Creates spans for tracking the time required to compose the wrapped [content], and a span for its
 * initial draw.
 *
 * Spans are approximate and include work performed by any composables [content] invokes. Abandoned
 * recompositions are ignored.
 */
@ExperimentalComposeUiApi
@Composable
public fun SentryTraced(
  tag: String,
  modifier: Modifier = Modifier,
  enableUserInteractionTracing: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val alreadyRendered = remember { ImmutableHolder(false) }
  val baseModifier = if (enableUserInteractionTracing) modifier.sentryTag(tag) else modifier

  val parentCompositionSpan = localSentryCompositionParentSpan.current.item
  val parentRenderingSpan = localSentryRenderingParentSpan.current.item
  val dateProvider = Sentry.getCurrentScopes().options.dateProvider

  // Only record spans if we have a parent for them.
  val compositionStart = parentCompositionSpan?.let { dateProvider.now() }

  Box(
    modifier =
      baseModifier.drawWithContent {
        if (alreadyRendered.item || parentRenderingSpan == null) {
          drawContent()
        } else {
          val renderStart = dateProvider.now()
          drawContent()
          val renderEnd = dateProvider.now()

          alreadyRendered.item = true
          recordRenderSpan(parentRenderingSpan, tag, renderStart, renderEnd)
        }
      },
    propagateMinConstraints = true,
  ) {
    content()
  }

  if (compositionStart != null) {
    val compositionEnd = dateProvider.now()

    SideEffect {
      recordCompositionSpan(
        parentSpan = parentCompositionSpan,
        tag = tag,
        startTimestamp = compositionStart,
        endTimestamp = compositionEnd,
      )
    }
  }
}

private fun getRootSpan(): ISpan? {
  var rootSpan: ISpan? = null
  Sentry.configureScope { rootSpan = it.transaction }
  return rootSpan
}

private fun recordCompositionSpan(
  parentSpan: ISpan?,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  parentSpan?.startChild(OP_COMPOSE, tag, startTimestamp)?.apply {
    spanContext.origin = OP_TRACE_ORIGIN
    finish(null, endTimestamp)
  }
}

private fun recordRenderSpan(
  parentSpan: ISpan?,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  parentSpan?.startChild(OP_RENDER, tag, startTimestamp)?.apply {
    finish(null, endTimestamp)
  }
}

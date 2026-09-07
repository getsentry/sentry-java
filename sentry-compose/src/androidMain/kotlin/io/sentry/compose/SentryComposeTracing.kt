package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
  getRootSpan()
    // Create a single parent span to own composition spans emitted by all SentryTraced composables
    // during the root's lifetime.
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
}

private val localSentryRenderingParentSpan = compositionLocalOf {
  getRootSpan()
    // Create a single parent span to own render spans emitted by all SentryTraced composables
    // during the root's lifetime.
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
}

/**
 * A substitute for Compose's `MutableState` that doesn't register itself with the snapshot system,
 * so mutating [value] never triggers recomposition.
 */
private class MutableRef<T>(var value: T)

/**
 * Creates a single span for tracking the time required to compose the wrapped [content], and a span
 * for its initial draw.
 *
 * Spans are approximate and include work performed by any composables [content] invokes. Abandoned
 * recompositions are ignored.
 *
 * Spans live under a set of parents shared by all `SentryTraced` composables. Every `SentryTraced`
 * contributes at most one `ui.compose` child and one `ui.render` child per parent lifetime:
 * ```
 * Root span
 * │
 * ├─ ui.compose.composition  "Jetpack Compose Initial Composition"
 * │   ├─ ui.compose   "product_info"
 * │   └─ ui.compose   "add_to_cart_button"
 * │
 * └─ ui.compose.rendering    "Jetpack Compose Initial Render"
 *     ├─ ui.render    "product_info"
 *     └─ ui.render    "add_to_cart_button"
 * ```
 *
 * Here `ui.compose.composition` and `ui.compose.rendering` are the shared parents. A `SentryTraced`
 * generates the "product_info" spans, and a separate `SentryTraced` generates the
 * "add_to_cart_button" spans.
 */
@ExperimentalComposeUiApi
@Composable
public fun SentryTraced(
  tag: String,
  modifier: Modifier = Modifier,
  enableUserInteractionTracing: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val baseModifier = if (enableUserInteractionTracing) modifier.sentryTag(tag) else modifier

  val parentCompositionSpan = localSentryCompositionParentSpan.current
  val parentRenderingSpan = localSentryRenderingParentSpan.current

  val alreadyComposed = remember(parentCompositionSpan) { MutableRef(false) }
  val alreadyRendered = remember(parentRenderingSpan) { MutableRef(false) }
  val dateProvider = Sentry.getCurrentScopes().options.dateProvider

  // Only record spans if we have a parent for them.
  val compositionStart =
    if (!alreadyComposed.value) parentCompositionSpan?.let { dateProvider.now() } else null

  Box(
    modifier =
      baseModifier.drawWithContent {
        if (alreadyRendered.value || parentRenderingSpan == null) {
          drawContent()
        } else {
          val renderStart = dateProvider.now()
          drawContent()
          val renderEnd = dateProvider.now()

          alreadyRendered.value = true
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

      alreadyComposed.value = true
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
    spanContext.origin = OP_TRACE_ORIGIN
    finish(null, endTimestamp)
  }
}

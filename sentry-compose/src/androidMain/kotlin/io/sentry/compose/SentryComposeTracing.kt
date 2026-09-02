package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.RememberObserver
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

/*
 * TODO ADAM: Follow-on PR focused on semantics:
 *  - Remove onAbandoned() span.
 *  - Only emit "Initial Composition" span once.
 *
 * ...and then the LocalSentrySpan PR fixing the stale parent transaction issue.
 */

private const val OP_PARENT_COMPOSITION = "ui.compose.composition"
private const val OP_COMPOSE = "ui.compose"

private const val OP_PARENT_RENDER = "ui.compose.rendering"
private const val OP_RENDER = "ui.render"

private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"

private const val TAG_KEY_COMPOSITION_RESULT = "composition.result"
private const val TAG_VALUE_COMPOSITION_ABANDONED = "abandoned"
private const val TAG_VALUE_COMPOSITION_SUCCESSFUL = "success"

@Immutable internal class ImmutableHolder<T>(var item: T)

/**
 * A [RememberObserver] that records an [OP_COMPOSE] span whenever composition succeeds or is
 * abandoned.
 *
 * Span start time is pegged to the provided [startTimestamp].
 */
internal class CompositionSpanRecorder(
  private val parentSpan: ISpan?,
  private val tag: String,
  private val startTimestamp: SentryDate,
) : RememberObserver {

  override fun onRemembered() {
    recordCompositionSpan(resultTag = TAG_VALUE_COMPOSITION_SUCCESSFUL)
  }

  override fun onForgotten() = Unit

  override fun onAbandoned() {
    recordCompositionSpan(resultTag = TAG_VALUE_COMPOSITION_ABANDONED)
  }

  private fun recordCompositionSpan(resultTag: String) {
    val endTimestamp = Sentry.getCurrentScopes().options.dateProvider.now()

    parentSpan
      ?.startChild(
        OP_COMPOSE,
        tag,
        SpanOptions().apply { this.startTimestamp = this@CompositionSpanRecorder.startTimestamp },
      )
      ?.apply {
        spanContext.origin = OP_TRACE_ORIGIN
        setTag(TAG_KEY_COMPOSITION_RESULT, resultTag)
        finish(null, endTimestamp)
      }
  }
}

private fun getRootSpan(): ISpan? {
  var rootSpan: ISpan? = null
  Sentry.configureScope { rootSpan = it.transaction }
  return rootSpan
}

private fun startRenderSpan(
  parentRenderingSpan: ISpan?,
  tag: String,
): ISpan? =
  parentRenderingSpan?.startChild(OP_RENDER, tag)?.apply { spanContext.origin = OP_TRACE_ORIGIN }

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

/**
 * Creates separate spans for the time required to compose the wrapped [content] and for its first
 * draw.
 *
 * Spans are approximate and include work performed by any composables [content] invokes.
 * Time-to-compose spans are produced for every composition attempt, and
 * [a result tag][TAG_KEY_COMPOSITION_RESULT] indicates whether the composition succeeded.
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

  val compositionStartTimestamp = Sentry.getCurrentScopes().options.dateProvider.now()
  remember(parentCompositionSpan, tag, compositionStartTimestamp) {
    CompositionSpanRecorder(parentCompositionSpan, tag, compositionStartTimestamp)
  }

  Box(
    modifier =
      baseModifier.drawWithContent {
        val renderSpan =
          if (alreadyRendered.item) {
            null
          } else {
            startRenderSpan(parentRenderingSpan, tag).also { alreadyRendered.item = true }
          }

        try {
          drawContent()
        } finally {
          renderSpan?.finish()
        }
      },
    propagateMinConstraints = true,
  ) {
    content()
  }
}

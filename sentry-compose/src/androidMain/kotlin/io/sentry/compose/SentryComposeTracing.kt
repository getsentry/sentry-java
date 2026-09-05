package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.sentry.ISpan
import io.sentry.Instrumenter
import io.sentry.NoOpSpan
import io.sentry.Sentry
import io.sentry.SentryDate
import io.sentry.SpanOptions
import io.sentry.compose.SentryModifier.sentryTag
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private const val DESCRIPTION_COMPOSITION_PARENT = "Jetpack Compose Initial Composition"
private const val OP_COMPOSITION_PARENT = "ui.compose.composition"
private const val OP_COMPOSITION_CHILD = "ui.compose"

private const val DESCRIPTION_RENDER_PARENT = "Jetpack Compose Initial Render"
private const val OP_RENDER_PARENT = "ui.compose.rendering"
private const val OP_RENDER_CHILD = "ui.render"

private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"

/**
 * Creates a span for the initial composition of the wrapped [content], and a span for its initial
 * rendering.
 *
 * Spans are approximate and include work performed by any composables [content] invokes. Abandoned
 * recompositions are ignored.
 *
 * **Span organization**
 *
 * All spans produced are rooted under an owner span defined by the environment `SentryTraced` runs
 * in. `SentryTraced` composables with the same owner share two common parent spans
 * (`ui.compose.composition` and `ui.compose.rendering`). Each `SentryTraced` in the group emits at
 * most one `ui.compose` span to the composition parent and one `ui.render` span to the render
 * parent.
 *
 * The end result looks something like this:
 * ```
 * Owner span
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
 * (Here, there were only two `SentryTraced` composables in the group. One emitted "product_info"
 * spans, the other emitted "add_to_cart_button" spans.)
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
  val scopes = Sentry.getCurrentScopes()
  val ownerSpan = scopes.transaction ?: NoOpSpan.getInstance()

  val alreadyComposed = remember(ownerSpan) { MutableRef(false) }
  val alreadyRendered = remember(ownerSpan) { MutableRef(false) }
  val shouldRecordSpans = !ownerSpan.dropsChildSpans

  val dateProvider = scopes.options.dateProvider
  val compositionStart =
    if (shouldRecordSpans && !alreadyComposed.value) dateProvider.now() else null

  Box(
    modifier =
      baseModifier.drawWithContent {
        if (!shouldRecordSpans || alreadyRendered.value) {
          drawContent()
          return@drawWithContent
        }

        val renderStart = dateProvider.now()
        drawContent()
        val renderEnd = dateProvider.now()

        alreadyRendered.value = true
        recordRenderSpan(ownerSpan, tag, renderStart, renderEnd)
      },
    propagateMinConstraints = true,
  ) {
    content()
  }

  if (compositionStart != null) {
    val compositionEnd = dateProvider.now()

    SideEffect {
      alreadyComposed.value = true
      recordCompositionSpan(ownerSpan, tag, compositionStart, compositionEnd)
    }
  }
}

private fun recordCompositionSpan(
  ownerSpan: ISpan,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  val parentSpan = ParentSpans.getOrCreateCompositionSpan(ownerSpan, startTimestamp) ?: return

  parentSpan.startChild(OP_COMPOSITION_CHILD, tag, startTimestamp).apply {
    spanContext.origin = OP_TRACE_ORIGIN
    finish(null, endTimestamp)
  }
}

private fun recordRenderSpan(
  ownerSpan: ISpan,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  val parentSpan = ParentSpans.getOrCreateRenderSpan(ownerSpan, startTimestamp) ?: return

  parentSpan.startChild(OP_RENDER_CHILD, tag, startTimestamp).apply {
    spanContext.origin = OP_TRACE_ORIGIN
    finish(null, endTimestamp)
  }
}

/**
 * Returns true if spans parented under the receiver will be dropped (and therefore aren't worth
 * creating in the first place).
 */
private val ISpan.dropsChildSpans: Boolean
  // NoOp spans return false for isFinished, so we check for no-op status directly.
  get() = this.isFinished || this.isNoOp

/**
 * Manages the creation of parent [OP_COMPOSITION_PARENT] and [OP_RENDER_PARENT] spans as owner
 * spans rotate over time. It does so for all [SentryTraced] instances throughout the app process.
 * (Process-wide logic and state lives in the companion object; per-SentryTraced state is
 * implemented by the instance properties.)
 *
 * Under the hood this class tracks which parent spans have been created for which owner span, so it
 * knows when new parent spans need to be created. But it doesn't own the lifecycle of either and
 * holds only weak references.
 *
 * **Not threadsafe:** Access must be confined to Compose UI-thread callbacks.
 */
private class ParentSpans {

  private var compositionParentSpan: WeakReference<ISpan>? = null
  private var renderParentSpan: WeakReference<ISpan>? = null

  companion object {

    private val ownerSpanToParentSpans = WeakHashMap<ISpan, ParentSpans>()

    fun getOrCreateCompositionSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
      getFor(ownerSpan).getOrCreateCompositionSpan(ownerSpan, startTimestamp)

    fun getOrCreateRenderSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
      getFor(ownerSpan).getOrCreateRenderSpan(ownerSpan, startTimestamp)

    private fun getFor(ownerSpan: ISpan): ParentSpans =
      ownerSpanToParentSpans.getOrPut(ownerSpan) { ParentSpans() }
  }

  private fun getOrCreateCompositionSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
    getOrCreate(
      ownerSpan = ownerSpan,
      startTimestamp = startTimestamp,
      cached = compositionParentSpan,
      operation = OP_COMPOSITION_PARENT,
      description = DESCRIPTION_COMPOSITION_PARENT,
    ) {
      compositionParentSpan = it
    }

  private fun getOrCreateRenderSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
    getOrCreate(
      ownerSpan = ownerSpan,
      startTimestamp = startTimestamp,
      cached = renderParentSpan,
      operation = OP_RENDER_PARENT,
      description = DESCRIPTION_RENDER_PARENT,
    ) {
      renderParentSpan = it
    }

  private fun getOrCreate(
    ownerSpan: ISpan,
    startTimestamp: SentryDate,
    cached: WeakReference<ISpan>?,
    operation: String,
    description: String,
    setCached: (WeakReference<ISpan>) -> Unit,
  ): ISpan? {
    cached
      ?.get()
      ?.takeUnless { it.dropsChildSpans }
      ?.let {
        return it
      }

    val parentSpan =
      ownerSpan.startChild(
        operation,
        description,
        startTimestamp,
        Instrumenter.SENTRY,
        SpanOptions().apply {
          isTrimStart = true
          isTrimEnd = true
          isIdle = true
        },
      )

    if (parentSpan.dropsChildSpans) {
      return null
    }

    parentSpan.spanContext.origin = OP_TRACE_ORIGIN
    setCached(WeakReference(parentSpan))
    return parentSpan
  }
}

/**
 * A substitute for Compose's `MutableState` that doesn't register itself with the snapshot system,
 * so mutating [value] won't trigger recomposition.
 */
private class MutableRef<T>(var value: T)

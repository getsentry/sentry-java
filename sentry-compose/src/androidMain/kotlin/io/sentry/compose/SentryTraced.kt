@file:JvmName("SentryComposeTracingKt")

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

private const val DESCRIPTION_COMPOSITION_BUCKET = "Jetpack Compose Initial Composition"
private const val OP_COMPOSITION_BUCKET = "ui.compose.composition"
private const val OP_COMPOSITION_SPAN = "ui.compose"

private const val DESCRIPTION_RENDER_BUCKET = "Jetpack Compose Initial Render"
private const val OP_RENDER_BUCKET = "ui.compose.rendering"
private const val OP_RENDER_SPAN = "ui.render"

private const val OP_TRACE_ORIGIN = "auto.ui.jetpack_compose"

/**
 * Creates a span for the initial composition of the wrapped [content], and a span for its initial
 * rendering, each of which lives under a shared "bucket" span (see "Span organization" below).
 *
 * Spans are approximate and include work performed by any composables [content] invokes. Abandoned
 * recompositions are ignored.
 *
 * **Span organization**
 *
 * All spans produced are rooted under an owner span defined by the environment `SentryTraced` runs
 * in. `SentryTraced` composables with the same owner share two common "bucket" spans
 * (`ui.compose.composition` and `ui.compose.rendering`). Each `SentryTraced` in the group emits at
 * most one `ui.compose` span to the composition bucket and one `ui.render` span to the render
 * bucket.
 *
 * The end result looks something like this:
 * ```
 * Owner span
 * │
 * ├─ ui.compose.composition  "Jetpack Compose Initial Composition"
 * │   ├─ ui.compose   "marketing_banner"
 * │   ├─ ui.compose   "product_info"
 * │   └─ ui.compose   "add_to_cart_button"
 * │
 * └─ ui.compose.rendering    "Jetpack Compose Initial Render"
 *     ├─ ui.render    "marketing_banner"
 *     ├─ ui.render    "product_info"
 *     └─ ui.render    "add_to_cart_button"
 * ```
 *
 * (Here, there were three `SentryTraced` composables in the owner group. One emitted
 * "marketing_banner" spans, another "product_info" spans, and another "add_to_cart_button" spans.)
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

/**
 * Creates a [OP_COMPOSITION_SPAN] under the [ownerSpan]'s composition bucket.
 *
 * If the owner doesn't yet have a composition bucket, this method creates one for its own use and
 * for use by other `SentryTraced` composables in the same owner group.
 */
private fun recordCompositionSpan(
  ownerSpan: ISpan,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  val bucketSpan = BucketSpans.getOrCreateCompositionSpan(ownerSpan, startTimestamp) ?: return

  bucketSpan
    .startChild(
      OP_COMPOSITION_SPAN,
      tag,
      startTimestamp,
      Instrumenter.SENTRY,
      SpanOptions().apply { origin = OP_TRACE_ORIGIN },
    )
    .run { finish(null, endTimestamp) }
}

/**
 * Creates a [OP_RENDER_SPAN] under the [ownerSpan]'s render bucket.
 *
 * If the owner doesn't yet have a render bucket, this method creates one for its own use and for
 * use by other `SentryTraced` composables in the same owner group.
 */
private fun recordRenderSpan(
  ownerSpan: ISpan,
  tag: String,
  startTimestamp: SentryDate,
  endTimestamp: SentryDate,
) {
  val bucketSpan = BucketSpans.getOrCreateRenderSpan(ownerSpan, startTimestamp) ?: return

  bucketSpan
    .startChild(
      OP_RENDER_SPAN,
      tag,
      startTimestamp,
      Instrumenter.SENTRY,
      SpanOptions().apply { origin = OP_TRACE_ORIGIN },
    )
    .run { finish(null, endTimestamp) }
}

/**
 * Returns true if spans parented under the receiver will be dropped (and therefore aren't worth
 * creating in the first place).
 */
private val ISpan.dropsChildSpans: Boolean
  // NoOp spans return false for isFinished, so we check for no-op status directly.
  get() = this.isFinished || this.isNoOp

/**
 * Manages the creation of [OP_COMPOSITION_BUCKET] and [OP_RENDER_BUCKET] spans as owner spans
 * rotate over time. It does so for all [SentryTraced] instances throughout the app process.
 * (Process-wide logic and state lives in the companion object; per-`SentryTraced` state is
 * implemented by the instance properties.)
 *
 * Under the hood this class tracks which bucket spans have been created for which owner span, so it
 * knows when new bucket spans need to be created. But it doesn't own the lifecycle of either and
 * holds only weak references.
 *
 * **Not threadsafe:** Access must be confined to Compose UI-thread callbacks.
 */
private class BucketSpans {

  // Bucket spans must be weakly held because spans keep a reference to their owning transaction. If
  // the owning transaction is the owner span or an ancestor of it, a strong reference here would
  // interfere with cleanup of the corresponding ownerSpanToBucketSpans entry.
  private var compositionBucketSpan: WeakReference<ISpan>? = null
  private var renderBucketSpan: WeakReference<ISpan>? = null

  companion object {

    private val ownerSpanToBucketSpans = WeakHashMap<ISpan, BucketSpans>()

    fun getOrCreateCompositionSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
      getFor(ownerSpan).getOrCreateCompositionSpan(ownerSpan, startTimestamp)

    fun getOrCreateRenderSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
      getFor(ownerSpan).getOrCreateRenderSpan(ownerSpan, startTimestamp)

    private fun getFor(ownerSpan: ISpan): BucketSpans =
      ownerSpanToBucketSpans.getOrPut(ownerSpan) { BucketSpans() }
  }

  private fun getOrCreateCompositionSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
    getOrCreate(
      ownerSpan = ownerSpan,
      startTimestamp = startTimestamp,
      cached = compositionBucketSpan,
      operation = OP_COMPOSITION_BUCKET,
      description = DESCRIPTION_COMPOSITION_BUCKET,
    ) {
      compositionBucketSpan = it
    }

  private fun getOrCreateRenderSpan(ownerSpan: ISpan, startTimestamp: SentryDate): ISpan? =
    getOrCreate(
      ownerSpan = ownerSpan,
      startTimestamp = startTimestamp,
      cached = renderBucketSpan,
      operation = OP_RENDER_BUCKET,
      description = DESCRIPTION_RENDER_BUCKET,
    ) {
      renderBucketSpan = it
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

    val bucketSpan =
      ownerSpan.startChild(
        operation,
        description,
        startTimestamp,
        Instrumenter.SENTRY,
        SpanOptions().apply {
          origin = OP_TRACE_ORIGIN
          isTrimStart = true
          isTrimEnd = true
          isIdle = true
        },
      )

    if (bucketSpan.dropsChildSpans) {
      return null
    }
    setCached(WeakReference(bucketSpan))
    return bucketSpan
  }
}

/**
 * A substitute for Compose's `MutableState` that doesn't register itself with the snapshot system,
 * so mutating [value] won't trigger recomposition.
 */
private class MutableRef<T>(var value: T)

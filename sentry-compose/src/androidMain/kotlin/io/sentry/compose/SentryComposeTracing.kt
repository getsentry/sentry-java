package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.sentry.IScopes
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

// Defaults to null rather than eagerly resolving Sentry.getCurrentScopes(): a CompositionLocal's
// default value is computed at most once for the process lifetime, so a non-null default would
// permanently cache whatever scopes were current on first read (e.g. a NoOp scopes if read before
// Sentry.init()). SentryTraced instead falls back to Sentry.getCurrentScopes() on every call when
// no value has been explicitly provided, so it always reflects the current scopes.
public val LocalSentryScopes: ProvidableCompositionLocal<IScopes?> = staticCompositionLocalOf {
  null
}

private fun getRootSpan(scopes: IScopes): ISpan? {
  var rootSpan: ISpan? = null
  scopes.configureScope { rootSpan = it.transaction }
  return rootSpan
}

private fun createCompositionParentSpan(scopes: IScopes): ImmutableHolder<ISpan?> =
  ImmutableHolder(
    getRootSpan(scopes)
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

private fun createRenderingParentSpan(scopes: IScopes): ImmutableHolder<ISpan?> =
  ImmutableHolder(
    getRootSpan(scopes)
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

private class RootSpans {
  // Entries are cleaned up deterministically via reference counting (see retain/release) rather
  // than relying on GC to reclaim a weakly-keyed map: a value here (ImmutableHolder -> Span)
  // strongly references the IScopes that created it (Span keeps its Scopes alive), so a
  // WeakHashMap keyed on IScopes would never actually evict its own key, and a value wrapped in a
  // WeakReference could be collected between recompositions even while a SentryTraced call under
  // that scopes is still in composition, silently breaking root span sharing.
  val compositionSpans = HashMap<IScopes, ImmutableHolder<ISpan?>>()
  val renderingSpans = HashMap<IScopes, ImmutableHolder<ISpan?>>()
  private val refCounts = HashMap<IScopes, Int>()

  fun retain(scopes: IScopes) {
    refCounts[scopes] = (refCounts[scopes] ?: 0) + 1
  }

  fun release(scopes: IScopes) {
    val remaining = (refCounts[scopes] ?: 1) - 1
    if (remaining <= 0) {
      refCounts.remove(scopes)
      // These are idle spans: they only auto-finish when the whole transaction finishes, so
      // evicting them from the cache without finishing them here would leave them open on the
      // transaction. If the same scopes is used again later, a fresh pair would be created
      // alongside the still-open, now-untracked originals, showing up as duplicate root spans.
      compositionSpans.remove(scopes)?.item?.finish()
      renderingSpans.remove(scopes)?.item?.finish()
    } else {
      refCounts[scopes] = remaining
    }
  }
}

// Retains scopes in the constructor so it always runs synchronously during composition, before
// any effect-phase work for this frame (including another SentryTraced call's dispose): Compose
// runs the dispose of an outgoing node before the effects of an incoming one in the same
// recomposition, so retaining from an effect could let a shared entry's refcount hit zero (and
// get evicted) between an old and a new SentryTraced call sharing the same scopes. Releasing from
// both onForgotten and onAbandoned (rather than a DisposableEffect) also covers a composition
// that never commits (e.g. an exception elsewhere during composition), which would otherwise
// leave the retain in place with no matching release.
private class ScopesRetention(private val rootSpans: RootSpans, private val scopes: IScopes) :
  RememberObserver {
  init {
    rootSpans.retain(scopes)
  }

  override fun onRemembered() {}

  override fun onForgotten() {
    rootSpans.release(scopes)
  }

  override fun onAbandoned() {
    rootSpans.release(scopes)
  }
}

private fun getOrCreateParentSpan(
  map: MutableMap<IScopes, ImmutableHolder<ISpan?>>,
  scopes: IScopes,
  create: (IScopes) -> ImmutableHolder<ISpan?>,
): ImmutableHolder<ISpan?> =
  // Only cache the holder once it actually contains a span; a null result (no transaction bound
  // to the scopes yet) is recomputed on the next call so a later transaction is still picked up.
  map[scopes] ?: create(scopes).also { if (it.item != null) map[scopes] = it }

// Cached once per Composition and shared by every SentryTraced call within it, mirroring the
// old eagerly-computed `compositionLocalOf { ... }` default (which Compose resolves once and
// reuses for every `.current` read that has no ancestor Provider, sibling or not). Keyed per
// IScopes so distinct custom scopes each get their own root span instead of colliding.
private val LocalRootSpans = staticCompositionLocalOf { RootSpans() }

@ExperimentalComposeUiApi
@Composable
public fun SentryTraced(
  tag: String,
  modifier: Modifier = Modifier,
  enableUserInteractionTracing: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val scopes = LocalSentryScopes.current ?: Sentry.getCurrentScopes()
  val rootSpans = LocalRootSpans.current
  remember(rootSpans, scopes) { ScopesRetention(rootSpans, scopes) }
  val parentCompositionSpan =
    getOrCreateParentSpan(rootSpans.compositionSpans, scopes, ::createCompositionParentSpan)
  val parentRenderingSpan =
    getOrCreateParentSpan(rootSpans.renderingSpans, scopes, ::createRenderingParentSpan)

  val compositionSpan =
    parentCompositionSpan.item?.startChild(OP_COMPOSE, tag)?.apply {
      spanContext.origin = OP_TRACE_ORIGIN
    }
  val firstRendered = remember { ImmutableHolder(false) }

  val baseModifier = if (enableUserInteractionTracing) modifier.sentryTag(tag) else modifier

  Box(
    modifier =
      baseModifier.drawWithContent {
        val renderSpan =
          if (!firstRendered.item) {
            parentRenderingSpan.item?.startChild(OP_RENDER, tag)
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

package io.sentry.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
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
import java.lang.ref.WeakReference
import java.util.WeakHashMap

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
  // Weakly keyed so scopes instances that are no longer referenced elsewhere (e.g. a short-lived
  // custom scopes provided via LocalSentryScopes for a finished screen/session) can be garbage
  // collected instead of being pinned here for the lifetime of the root Composition. The cached
  // holder is itself wrapped in a WeakReference: its ISpan strongly references the scopes that
  // created it (Span keeps its Scopes alive), so storing the holder directly as the map value
  // would let that strong value -> key path keep every scopes instance permanently reachable,
  // defeating the WeakHashMap keying entirely.
  val compositionSpans = WeakHashMap<IScopes, WeakReference<ImmutableHolder<ISpan?>>>()
  val renderingSpans = WeakHashMap<IScopes, WeakReference<ImmutableHolder<ISpan?>>>()
}

private fun getOrCreateParentSpan(
  map: MutableMap<IScopes, WeakReference<ImmutableHolder<ISpan?>>>,
  scopes: IScopes,
  create: (IScopes) -> ImmutableHolder<ISpan?>,
): ImmutableHolder<ISpan?> =
  // Only cache the holder once it actually contains a span; a null result (no transaction bound
  // to the scopes yet) is recomputed on the next call so a later transaction is still picked up.
  // A cleared reference (holder was collected) is also recomputed rather than reused.
  map[scopes]?.get() ?: create(scopes).also { if (it.item != null) map[scopes] = WeakReference(it) }

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

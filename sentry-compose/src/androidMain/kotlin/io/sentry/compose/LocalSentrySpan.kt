package io.sentry.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import io.sentry.ISpan
import io.sentry.NoOpSpan
import io.sentry.Sentry

/**
 * A [ProvidableCompositionLocal] for delivering [ISpan]s to composable subtrees. The delivered span
 * should be used to parent any spans the receiving subtree produces.
 *
 * Lets child composables remain agnostic about the [ISpan] hierarchies constructed by their
 * ancestors.
 */
@ExperimentalComposeUiApi
public val LocalSentrySpan: ProvidableCompositionLocal<ISpan> = compositionLocalOf {
  UnsetSentrySpan
}

/**
 * A wrapper for any composable subtree that should be passed the provided [span] via
 * [LocalSentrySpan].
 *
 * The span is [normalized][normalize].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProvideSentrySpan(span: ISpan?, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalSentrySpan provides span.normalize()) {
    content()
  }
}

/**
 * Returns the receiver as-is unless it's an [UnsetSentrySpan], in which case it returns the current
 * transaction.
 *
 * `*Bootstrap` methods are for internal use only. They're designed for scenarios where
 * [LocalSentrySpan] hasn't been set, but we want to provide a reasonable alternative or fall back
 * to preexisting behavior.
 */
internal fun ISpan.orBootstrapCurrentTransaction(): ISpan =
  if (this.isUnset()) {
    Sentry.getCurrentScopes().transaction.normalize()
  } else {
    this
  }

/**
 * A sentinel span indicating that [LocalSentrySpan] hasn't been set. For internal use only.
 *
 * Note: This must be a distinct type from [NoOpSpan] so that [isUnset] can determine whether
 * `LocalSentrySpan` was set with a `NoOpSpan` or was never set at all.
 *
 * Our own implementations need that information because:
 *
 * 1. we should always honor the value of `LocalSentrySpan` if deliberately set, even when it vends
 *    a `NoOpSpan`; but
 *
 * 2. we'll often want supply our own default parent span if `LocalSentrySpan` hasn't been set.
 *
 * A sentinel type lets us do so without complicating our public API for a distinction irrelevant to
 * host apps.
 */
private object UnsetSentrySpan : ISpan by NoOpSpan.getInstance()

private fun ISpan.isUnset(): Boolean = this === UnsetSentrySpan

private fun ISpan?.normalize(): ISpan =
  when {
    this === UnsetSentrySpan || this is NoOpSpan -> this
    this == null || this.isFinished -> NoOpSpan.getInstance()
    else -> this
  }

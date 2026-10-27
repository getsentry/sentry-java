package io.sentry.samples.android.navigation.nav3

import android.app.Activity
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun tagCurrentNavigationSampleScenario(scenarioLabel: String) {
  Sentry.getSpan()?.setTag(NAVIGATION_SAMPLE_SCENARIO_TAG, scenarioLabel)
  Sentry.configureScope { scope ->
    scope.withTransaction { transaction ->
      transaction?.setTag(NAVIGATION_SAMPLE_SCENARIO_TAG, scenarioLabel)
    }
  }
}

internal fun SentryTransaction.navigationSampleScenarioLabel(): String =
  getTag(NAVIGATION_SAMPLE_SCENARIO_TAG)
    ?: getTag(NAV2_SCENARIO_TAG)
    ?: getTag(NAV3_SCENARIO_TAG)
    ?: UNKNOWN_NAVIGATION_SCENARIO_LABEL

/**
 * Applies the sample's "Activity ui.load transaction" toggle to [activity].
 *
 * Call from `onCreate` after `super.onCreate`, once the SDK has started the Activity's ui.load
 * transaction. When [enabled] is false, the transaction is cancelled if it's bound to the scope.
 * It's also dropped before sending, because the SDK doesn't bind it when another transaction (such
 * as the previous Activity's nav transaction) is already bound, and the sample can't reach an
 * unbound ui.load transaction to cancel it.
 */
internal fun applyActivityUiLoadTransactionSetting(activity: Activity, enabled: Boolean) {
  // Matches the transaction name ActivityLifecycleIntegration uses.
  val activityName = activity.javaClass.simpleName
  if (enabled) {
    SuppressedActivityUiLoadTransactions.activityNames.remove(activityName)
    return
  }
  SuppressedActivityUiLoadTransactions.install()
  SuppressedActivityUiLoadTransactions.activityNames.add(activityName)
  cancelCurrentActivityUiLoadTransaction()
}

/** Drops ui.load transactions for sample Activities created with the ui.load toggle off. */
private object SuppressedActivityUiLoadTransactions : EventProcessor {
  val activityNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

  @Volatile private var installed = false

  fun install() {
    if (installed) return
    synchronized(this) {
      if (installed) return
      Sentry.getCurrentScopes().options.addEventProcessor(this)
      installed = true
    }
  }

  override fun process(transaction: SentryTransaction, hint: Hint): SentryTransaction? {
    val isSuppressed =
      transaction.contexts.trace?.operation == ACTIVITY_UI_LOAD_OP &&
        transaction.transaction in activityNames
    return if (isSuppressed) null else transaction
  }
}

internal fun cancelCurrentActivityUiLoadTransaction() {
  Sentry.configureScope { scope ->
    scope.withTransaction { transaction ->
      if (transaction?.operation == ACTIVITY_UI_LOAD_OP) {
        transaction.forceFinish(SpanStatus.CANCELLED, false, null)
        scope.clearTransaction()
      }
    }
  }
}

internal suspend fun recordSimulatedBackgroundSpan(routeName: String) {
  val parentSpan = Sentry.getSpan()
  suspendCancellableCoroutine { continuation ->
    val worker = Thread {
      val span =
        parentSpan?.startChild(
          "test.navigation.background_work",
          "Nav3 /$routeName background work",
        )
      span?.setData("sample.background_work", true)
      try {
        Thread.sleep(BACKGROUND_WORK_MILLIS)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      } finally {
        span?.finish()
        if (continuation.isActive) {
          continuation.resume(Unit)
        }
      }
    }
    continuation.invokeOnCancellation { worker.interrupt() }
    worker.start()
  }
}

internal fun emitSampleNavigationSpan(routeName: String) {
  val span =
    Sentry.getSpan()
      ?.startChild(
        "test.navigation.emit_span",
        "Nav3 /$routeName emit a span",
      )
  span?.setData("sample.emit_span", true)
  span?.finish()
}

internal const val SENTRY_FLUSH_TIMEOUT_MILLIS = 5000L

private const val BACKGROUND_WORK_MILLIS = 1000L
private const val NAV2_SCENARIO_TAG = "sample_nav2_scenario"
internal const val NAV3_SCENARIO_TAG = "sample_nav3_scenario"
internal const val NAVIGATION_SAMPLE_ACTIVITY_TAG = "sample_navigation_activity"
internal const val NAVIGATION_SAMPLE_SESSION_TAG = "sample_navigation_session"
internal const val NAVIGATION_SAMPLE_SCENARIO_TAG = "sample_navigation_scenario"
private const val UNKNOWN_NAVIGATION_SCENARIO_LABEL = "Unknown"
internal const val ACTIVITY_UI_LOAD_OP = "ui.load"

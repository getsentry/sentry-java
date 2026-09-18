package io.sentry.samples.android.navigation.common

import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.protocol.SentryTransaction
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Optional work navigation sample routes can perform after they become active. */
internal enum class RouteWorkOption(val label: String, val tagName: String) {
  HTTP_REQUEST("HTTP request", "http_request"),
  MANUAL_CHILD_SPAN("Manual child span", "manual_child_span"),
}

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
    ?: UNKNOWN_NAVIGATION_SCENARIO_LABEL

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

internal suspend fun recordSimulatedBackgroundSpan(routeName: String, navName: String = "Nav2") {
  val parentSpan = Sentry.getSpan()
  suspendCancellableCoroutine { continuation ->
    val worker = Thread {
      val span =
        parentSpan?.startChild(
          "test.navigation.background_work",
          "$navName /$routeName background work",
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

internal fun emitSampleNavigationSpan(routeName: String, navName: String = "Nav2") {
  val span =
    Sentry.getSpan()
      ?.startChild(
        "test.navigation.emit_span",
        "$navName /$routeName emit a span",
      )
  span?.setData("sample.emit_span", true)
  span?.finish()
}

internal const val SENTRY_FLUSH_TIMEOUT_MILLIS = 5000L

private const val BACKGROUND_WORK_MILLIS = 1000L
private const val NAV2_SCENARIO_TAG = "sample_nav2_scenario"
private const val NAVIGATION_SAMPLE_SCENARIO_TAG = "sample_navigation_scenario"
private const val UNKNOWN_NAVIGATION_SCENARIO_LABEL = "Unknown"
private const val ACTIVITY_UI_LOAD_OP = "ui.load"

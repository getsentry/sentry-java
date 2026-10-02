package io.sentry.samples.android.anr

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.anr.AnrProfilingIntegration
import io.sentry.samples.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Measures the overhead of [AnrProfilingIntegration] while the app is responsive.
 *
 * Pressing the button re-initializes the SDK with a minimal configuration, installs a
 * [MeasuringAnrProfilingIntegration] with the intervals passed via intent extras, and keeps it
 * running for [MEASUREMENT_DURATION_MS] inside the [MEASUREMENT_SECTION] trace section. After
 * [WORKLOAD_START_MS], a [MainThreadWorkload] backlog is enqueued on the main thread. The SDK is
 * closed afterwards, so nothing else runs once the measurement is done.
 *
 * Intent extras: [EXTRA_MODE] (`on` or `off`), [EXTRA_POLLING_INTERVAL_MS],
 * [EXTRA_SAMPLING_INTERVAL_MS], [EXTRA_SUSPICION_THRESHOLD_MS] and [EXTRA_ANIMATE].
 */
class AnrProfilingOverheadActivity : ComponentActivity() {

  private var status by mutableStateOf(STATUS_IDLE)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val enabled = intent.getStringExtra(EXTRA_MODE) != MODE_OFF
    val pollingIntervalMs =
      intent.getLongExtra(EXTRA_POLLING_INTERVAL_MS, AnrProfilingIntegration.POLLING_INTERVAL_MS)
    val samplingIntervalMs = intent.getLongExtra(EXTRA_SAMPLING_INTERVAL_MS, pollingIntervalMs)
    val suspicionThresholdMs = intent.getLongExtra(EXTRA_SUSPICION_THRESHOLD_MS, 1000)
    val animate = intent.getBooleanExtra(EXTRA_ANIMATE, true)

    val config =
      if (enabled) {
        "polling ${pollingIntervalMs}ms, sampling ${samplingIntervalMs}ms, " +
          "threshold ${suspicionThresholdMs}ms"
      } else {
        "AnrProfilingIntegration off"
      }

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          OverheadScreen(
            config = config,
            status = status,
            animate = animate,
            onRun = {
              runMeasurement(enabled, pollingIntervalMs, samplingIntervalMs, suspicionThresholdMs)
            },
          )
        }
      }
    }
  }

  private fun runMeasurement(
    enabled: Boolean,
    pollingIntervalMs: Long,
    samplingIntervalMs: Long,
    suspicionThresholdMs: Long,
  ) {
    if (status == STATUS_RUNNING) {
      return
    }
    status = STATUS_RUNNING

    SentryAndroid.init(this) { options ->
      options.tracesSampleRate = null
      options.profileSessionSampleRate = null
      options.sessionReplay.sessionSampleRate = null
      options.sessionReplay.onErrorSampleRate = null
      options.logs.isEnabled = false
      options.isEnableUserInteractionTracing = false
      options.isEnableAutoSessionTracking = false
      options.isEnableSpotlight = false
      options.feedbackOptions.isUseShakeGesture = false
      options.isDebug = false

      options.integrations.removeAll { it is AnrProfilingIntegration }
      if (enabled) {
        options.anrProfilingSampleRate = 1.0
        options.addIntegration(
          MeasuringAnrProfilingIntegration(
            pollingIntervalMs,
            samplingIntervalMs,
            suspicionThresholdMs,
          )
        )
      } else {
        options.anrProfilingSampleRate = null
      }
    }

    lifecycleScope.launch {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Trace.beginAsyncSection(MEASUREMENT_SECTION, 0)
      }
      delay(WORKLOAD_START_MS)
      MainThreadWorkload.post(Handler(Looper.getMainLooper()))
      delay(MEASUREMENT_DURATION_MS - WORKLOAD_START_MS)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Trace.endAsyncSection(MEASUREMENT_SECTION, 0)
      }
      withContext(Dispatchers.IO) { Sentry.close() }
      status = STATUS_DONE
    }
  }

  companion object {
    const val EXTRA_MODE = "mode"
    const val EXTRA_POLLING_INTERVAL_MS = "pollingIntervalMs"
    const val EXTRA_SAMPLING_INTERVAL_MS = "samplingIntervalMs"
    const val EXTRA_SUSPICION_THRESHOLD_MS = "suspicionThresholdMs"
    const val EXTRA_ANIMATE = "animate"
    const val MODE_OFF = "off"

    const val MEASUREMENT_SECTION = "AnrProfiling.measurement"
    const val MEASUREMENT_DURATION_MS = 10_000L
    const val WORKLOAD_START_MS = 2_000L

    const val BUTTON_TEXT = "Run 10 s measurement"
    const val STATUS_IDLE = "Idle"
    const val STATUS_RUNNING = "Measuring…"
    const val STATUS_DONE = "Measurement done"
  }
}

@Composable
private fun OverheadScreen(config: String, status: String, animate: Boolean, onRun: () -> Unit) {
  var seconds by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1000)
      seconds++
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    if (animate) {
      Spinner()
    }
    Text("Recompositions: $seconds", style = MaterialTheme.typography.titleMedium)
    Text(config, style = MaterialTheme.typography.bodyMedium)
    Text(status, style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onRun) { Text(AnrProfilingOverheadActivity.BUTTON_TEXT) }
  }
}

@Composable
private fun Spinner() {
  val rotation by
    rememberInfiniteTransition(label = "spinner")
      .animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
      )
  Image(
    painter = painterResource(R.drawable.sentry_glyph),
    contentDescription = "Loading",
    modifier = Modifier.size(96.dp).graphicsLayer { rotationZ = rotation },
  )
}

package io.sentry.samples.android

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import java.util.concurrent.Executors

class ProfilingActivity : ComponentActivity() {

  private val executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
  private var profileFinished = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { ProfilingScreen() } }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun ProfilingScreen() {
    val context = LocalContext.current
    val options = remember { Sentry.getCurrentScopes().options }
    val isPerfetto = remember { options.isUseProfilingManager && Build.VERSION.SDK_INT >= 35 }
    val isContinuousEnabled = remember { options.isContinuousProfilingEnabled }

    var showProgress by remember { mutableStateOf(false) }
    var manualActive by remember { mutableStateOf(false) }

    val statusText =
      when {
        !isContinuousEnabled -> stringResource(R.string.profiling_status_none)
        isPerfetto -> stringResource(R.string.profiling_status_perfetto)
        else -> stringResource(R.string.profiling_status_legacy)
      }

    Scaffold(topBar = { TopAppBar(title = { Text("Profiling") }) }) { innerPadding ->
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = statusText, fontWeight = FontWeight.Bold)

        Text("profiling.use-profiling-manager: ${options.isUseProfilingManager}")
        Text("Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")
        Text("traces.profiling.session-sample-rate: ${options.profileSessionSampleRate}")

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Button(
          onClick = {
            if (!manualActive) {
              Sentry.startProfiler()
              manualActive = true
              profileFinished = false
              showProgress = true

              val threads = 2
              repeat(threads) { executors.submit { runMathOperations() } }

              Toast.makeText(context, R.string.profiling_manual_started, Toast.LENGTH_SHORT).show()
            } else {
              Sentry.stopProfiler()
              manualActive = false
              profileFinished = true
              showProgress = false

              Toast.makeText(context, R.string.profiling_manual_stopped, Toast.LENGTH_SHORT).show()
            }
          }
        ) {
          Text(
            if (manualActive) stringResource(R.string.profiling_stop_manual)
            else stringResource(R.string.profiling_start_manual)
          )
        }

        if (showProgress) {
          CircularProgressIndicator()
        }
      }
    }
  }

  private fun runMathOperations() {
    while (!profileFinished) {
      fibonacci(25)
    }
  }

  private fun fibonacci(n: Int): Int =
    when {
      profileFinished -> n
      n <= 1 -> 1
      else -> fibonacci(n - 1) + fibonacci(n - 2)
    }
}

package io.sentry.samples.android

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object SentryBuddySampleIntegration {
  @JvmStatic @Suppress("UNUSED_PARAMETER") fun install(application: Application) = Unit

  fun isAvailable(): Boolean = false

  fun startCheckoutRecording() = Unit

  @Suppress("UNUSED_PARAMETER") fun recordStep(name: String) = Unit

  fun stopRecording(): String = "Sentry Buddy is only available in debug builds."

  @Composable
  fun BuddyDemoScreen() {
    MaterialTheme {
      Surface(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            "Sentry Buddy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )
          Text("Sentry Buddy is only available in debug builds.")
        }
      }
    }
  }
}

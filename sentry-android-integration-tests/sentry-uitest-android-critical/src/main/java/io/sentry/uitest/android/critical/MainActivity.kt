package io.sentry.uitest.android.critical

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.uitest.android.critical.NotificationHelper.showNotification
import java.io.File
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private lateinit var requestPermissionLauncher: ActivityResultLauncher<String?>

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)

    super.onCreate(savedInstanceState)
    val outboxPath =
      Sentry.getCurrentHub().options.outboxPath ?: throw RuntimeException("Outbox path is not set.")

    requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
          // Permission granted, show notification
          postNotification()
        } else {
          // Permission denied, handle accordingly
          Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
      }
    setContent {
      var appStartType by remember { mutableStateOf("") }

      LaunchedEffect(Unit) {
        delay(100)
        appStartType = AppStartMetrics.getInstance().appStartType.name
      }

      MaterialTheme {
        Surface {
          Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = "Welcome!")
            Text(text = "App Start Type: $appStartType")

            Button(onClick = { throw RuntimeException("Crash the test app.") }) { Text("Crash") }
            Button(onClick = { Sentry.close() }) { Text("Close SDK") }
            Button(
              onClick = {
                val file = File(outboxPath, "corrupted.envelope")
                val corruptedEnvelopeContent =
                  """
                                {"event_id":"1990b5bc31904b7395fd07feb72daf1c","sdk":{"name":"sentry.java.android","version":"7.21.0"}}
                                {"type":"test","length":50}
                                """
                    .trimIndent()
                file.writeText(corruptedEnvelopeContent)
                println("Wrote corrupted envelope to: ${file.absolutePath}")
              }
            ) {
              Text("Write Corrupted Envelope")
            }
            Button(onClick = { finish() }) { Text("Finish Activity") }
            Button(
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  postNotification()
                }
              }
            ) {
              Text("Trigger Notification")
            }
            Button(
              onClick = {
                startActivity(
                  Intent(this@MainActivity, MainActivity::class.java).apply {
                    addFlags(
                      Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                  }
                )
              }
            ) {
              Text("Launch Main Activity (singleTask)")
            }
          }
        }
      }
    }
  }

  fun postNotification() {
    NotificationHelper.showNotification(
      this@MainActivity,
      "Sentry Test Notification",
      "This is a test notification.",
    )
  }
}

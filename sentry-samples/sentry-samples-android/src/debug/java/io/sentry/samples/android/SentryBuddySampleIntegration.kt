package io.sentry.samples.android

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.BuddyFlowImportance
import io.sentry.android.buddy.BuddyFlowIntent
import io.sentry.android.buddy.BuddyFlowRecordingJsonSerializer
import io.sentry.android.buddy.SentryBuddy
import io.sentry.android.buddy.SentryBuddyOverlay

object SentryBuddySampleIntegration {
  @JvmStatic
  fun install(application: Application) {
    SentryBuddy.install(application)
  }

  fun isAvailable(): Boolean = true

  fun startCheckoutRecording() {
    SentryBuddy.startRecording(
      BuddyFlowIntent(
        name = "Sample Checkout",
        developerGoal = "Understand how this sample flow maps to Sentry telemetry.",
        importance = BuddyFlowImportance.BUSINESS_CRITICAL,
        data =
          linkedMapOf(
            "surface" to "sentry-samples-android",
            "demo" to "hackweek-sentry-buddy",
          ),
      )
    )
  }

  fun recordStep(name: String) {
    try {
      SentryBuddy.recordStep(name)
    } catch (_: IllegalStateException) {
      // The demo host is interactive even when Buddy is not actively recording.
    }
  }

  fun stopRecording(): String =
    BuddyFlowRecordingJsonSerializer.serialize(SentryBuddy.stopRecording())

  @Composable
  fun BuddyDemoScreen() {
    MaterialTheme {
      SentryBuddyOverlay { BuddyDemoHostContent() }
    }
  }

  @Composable
  private fun BuddyDemoHostContent() {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F5FA)) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Text(
          "Sentry Buddy",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "Interactive debug SDK demo. Tap the bubble to record a stand-in login flow, " +
            "then brief Buddy after the session."
        )
        SkeletonHero()
        DemoStep("Open login", "navigation Login → Home")
        DemoStep("Submit sign in", "http.client POST /auth")
        DemoStep("Load feed", "http.client GET /feed")
        DemoStep("Render profile", "ui.render Profile")
      }
    }
  }

  @Composable
  private fun SkeletonHero() {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(150.dp)
          .background(Color(0xFFEDEBF1), RoundedCornerShape(18.dp))
    )
  }

  @Composable
  private fun DemoStep(title: String, stepName: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Box(modifier = Modifier.size(54.dp).background(Color(0xFFE2E0E8), RoundedCornerShape(12.dp)))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(stepName, color = Color(0xFF6F6B7A))
      }
      Button(onClick = { recordStep(stepName) }) { Text("Step") }
    }
    Spacer(Modifier.height(2.dp))
  }
}

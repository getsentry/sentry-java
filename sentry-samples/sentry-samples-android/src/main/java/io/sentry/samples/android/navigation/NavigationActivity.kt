package io.sentry.samples.android.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class NavigationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) { NavigationSampleShell() }
      }
    }
  }
}

@Composable
private fun NavigationSampleShell() {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Navigation 3 Sample", style = MaterialTheme.typography.headlineSmall)
    Text(
      text = "Use this activity to exercise Sentry Navigation 3 spans.",
      modifier = Modifier.padding(top = 12.dp),
    )
  }
}

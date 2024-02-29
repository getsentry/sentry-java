package io.sentry.samples.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import kotlin.random.Random

class MetricsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Button(onClick = {
                            Sentry.metrics().increment("example.increment")
                        }) {
                            Text(text = "Increment")
                        }
                        Button(onClick = {
                            Sentry.metrics().distribution("example.distribution", Random.nextDouble())
                        }) {
                            Text(text = "Distribution")
                        }
                        Button(onClick = {
                            Sentry.metrics().gauge("example.gauge", Random.nextDouble())
                        }) {
                            Text(text = "Gauge")
                        }
                        Button(onClick = {
                            Sentry.metrics().set("example.gauge", Random.nextInt())
                        }) {
                            Text(text = "Set")
                        }
                    }
                }
            }
        }
    }
}

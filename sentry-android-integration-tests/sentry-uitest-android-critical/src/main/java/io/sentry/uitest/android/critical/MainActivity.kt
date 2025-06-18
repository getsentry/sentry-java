package io.sentry.uitest.android.critical

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.sentry.Sentry
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val outboxPath =
            Sentry.getCurrentHub().options.outboxPath
                ?: throw RuntimeException("Outbox path is not set.")

        setContent {
            MaterialTheme {
                Surface {
                    Column {
                        Text(text = "Welcome!")
                        Button(onClick = {
                            throw RuntimeException("Crash the test app.")
                        }) {
                            Text("Crash")
                        }
                        Button(onClick = {
                            Sentry.close()
                        }) {
                            Text("Close SDK")
                        }
                        Button(onClick = {
                            val file = File(outboxPath, "corrupted.envelope")
                            val corruptedEnvelopeContent =
                                """
                                {"event_id":"1990b5bc31904b7395fd07feb72daf1c","sdk":{"name":"sentry.java.android","version":"7.21.0"}}
                                {"type":"test","length":50}
                                """.trimIndent()
                            file.writeText(corruptedEnvelopeContent)
                            println("Wrote corrupted envelope to: ${file.absolutePath}")
                        }) {
                            Text("Write Corrupted Envelope")
                        }
                    }
                }
            }
        }
    }
}

package io.sentry.samples.android

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import kotlin.random.Random

class TestActivity : ComponentActivity() {

    var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRunning = true
        (0 until 100).forEach {
            createThread()
        }
        Thread {
            while (Sentry.getSpan() != null)
                Thread.sleep(100)
            val t = Sentry.startTransaction("test transaction", "Profile crash try")

            Thread {
                t.finish()
            }.start()
        }.start()
        setContent {
            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Button(onClick = {
                            Thread {
                                Log.e("test", "start debugs")
//                                (0 until 100).forEach {
                                    Thread {
//                                        Sentry.getSpan()?.finish()
//                                        Thread.sleep(Random.nextLong(300))
                                        val t = Sentry.startTransaction("test transaction", "Profile crash try")
                                        Thread.sleep(Random.nextLong(30))
                                        Log.e("test", "start debugs")
                                        Thread {
                                            t.finish()
                                        }.start()
                                        Log.e("test", "stop debugs")
                                    }.start()
//                                }
//                                (0 until 100).forEach {
//                                    Debug.startMethodTracingSampling("test", 8000000, 1000);
//                                    Debug.stopNativeTracing()
//                                    Thread.sleep(10)
//                                    Debug.stopMethodTracing()
//                                }
                                Log.e("test", "stop debugs")
                            }.start()
                        }) {
                            Text(text = "test profile")
                        }
                        List(20) {
                            Image(bitmap = generateBitmap().asImageBitmap(), contentDescription = "test image")
                        }
                    }
                }
            }
        }
        Sentry.reportFullyDisplayed()
    }

    private fun generateBitmap(): Bitmap {
        val bitmapSize = 100
        val r = Random.nextInt(256)
        val colors = (0 until (600 * 150)).map {
            Color.rgb(r, r, r)
        }.toIntArray()
        return Bitmap.createBitmap(colors, 600, 150, Bitmap.Config.ARGB_8888)
    }

    fun createThread() {
        Thread {
            while (isRunning) {
                val random = Random.nextInt(50, 1000)
                Thread.sleep(random.toLong())
                var x = 1L
                (0 until 10000000).forEach {
                    x *= it
                    x += random
                }
            }
        }.start()
    }
    override fun onStop() {
        super.onStop()
        isRunning = false
    }
}

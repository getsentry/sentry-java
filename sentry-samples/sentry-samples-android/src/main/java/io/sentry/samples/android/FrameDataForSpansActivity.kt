package io.sentry.samples.android

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanDataConvention
import io.sentry.TransactionOptions

class FrameDataForSpansActivity : ComponentActivity() {

    private val model = ViewModel()
    private var txn: ITransaction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Frame Data for Spans",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(text = "Tap to trigger a new frame render")
                        FrameControls(model)
                        Spacer(modifier = Modifier.size(24.dp))
                        Text(text = "Span Control")
                        SpanControls(model)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // ensure we have a top level txn to attach our spans to
        Sentry.getSpan()?.finish()
        val txnOpts = TransactionOptions().apply {
            idleTimeout = 100000
            deadlineTimeout = 100000
        }
        txn = Sentry.startTransaction("SlowAndFrozenFramesActivity", "ui.render", txnOpts)
    }

    override fun onStop() {
        super.onStop()
        txn?.finish()
    }
}

@Composable
fun FrameControls(viewModel: ViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        JankyButton(name = "Normal", delay = 5, viewModel.normalCount)
        JankyButton(name = "Slow", delay = 500, viewModel.slowCount)
        JankyButton(name = "Frozen", delay = 3000, viewModel.frozenCount)
    }
}

@Composable
fun ButtonWithoutIndication(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val m = if (enabled) {
        modifier
            .background(
                MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(25)
            )
            .pointerInput(Unit) {
                detectTapGestures {
                    onClick()
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        modifier
            .background(
                Color.LightGray,
                shape = RoundedCornerShape(25)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Box(
        modifier = m
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            content()
        }
    }
}

@Composable
fun JankyButton(name: String, delay: Long, counter: MutableIntState) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(25)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    counter.intValue += 1
                    Thread.sleep(delay)
                }
            }
    ) {
        Text(
            color = MaterialTheme.colorScheme.onPrimary,
            text = "$name: ${counter.intValue}"
        )
    }
}

@Composable
fun SpanControls(viewModel: ViewModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ButtonWithoutIndication(
                enabled = viewModel.lastSpan.value == null,
                onClick = {
                    viewModel.onStartSpanClicked()
                }
            ) {
                Text("Start")
            }

            ButtonWithoutIndication(
                enabled = viewModel.lastSpan.value != null,
                onClick = {
                    viewModel.onStopSpanClicked()
                }
            ) {
                Text("Stop")
            }

            ButtonWithoutIndication(
                enabled = viewModel.lastSpan.value != null,
                onClick = {
                    viewModel.onStopDelayedSpanClicked()
                }
            ) {
                Text("Stop in 1s")
            }
        }

        viewModel.lastSpanSummary.value?.let {
            Spacer(modifier = Modifier.size(12.dp))
            Text(text = "Last Span Summary", style = MaterialTheme.typography.headlineSmall)
            Text(it)
        }
    }
}

class ViewModel {
    val lastSpan = mutableStateOf<ISpan?>(null)
    val lastSpanSummary = mutableStateOf<String?>(null)
    val normalCount = mutableIntStateOf(0)
    val slowCount = mutableIntStateOf(0)
    val frozenCount = mutableIntStateOf(0)

    fun onStartSpanClicked() {
        normalCount.intValue = 0
        slowCount.intValue = 0
        frozenCount.intValue = 0

        lastSpan.value = Sentry.getSpan()?.startChild("op.span")
        lastSpanSummary.value = null
    }

    fun onStopDelayedSpanClicked() {
        Thread {
            Thread.sleep(1000)
            onStopSpanClicked()
        }.start()
    }

    @SuppressLint("PrivateApi")
    fun onStopSpanClicked() {
        lastSpan.value?.finish()

        lastSpanSummary.value = lastSpan.value?.let { span ->
            "${SpanDataConvention.FRAMES_SLOW}: ${span.getData(SpanDataConvention.FRAMES_SLOW)}\n" +
                "${SpanDataConvention.FRAMES_FROZEN}: ${span.getData(SpanDataConvention.FRAMES_FROZEN)}\n" +
                "${SpanDataConvention.FRAMES_TOTAL}: ${span.getData(SpanDataConvention.FRAMES_TOTAL)}\n" +
                "${SpanDataConvention.FRAMES_DELAY}: ${span.getData(SpanDataConvention.FRAMES_DELAY)}\n"
        }
        lastSpan.value = null
    }
}

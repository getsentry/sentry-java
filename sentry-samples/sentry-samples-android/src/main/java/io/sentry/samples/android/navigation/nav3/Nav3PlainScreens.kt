package io.sentry.samples.android.navigation.nav3

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.sentry.ISpan
import io.sentry.Sentry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Intentionally has no NavDisplay or SentryNavEffect; launch normally to stay in the same task. */
class Nav3PlainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      Nav3SampleTheme {
        PlainNav3Screen("Plain Activity", onBack = ::finish)
        // Let ui.load finish normally rather than wait for the sample's TTFD timeout.
        LaunchedEffect(Unit) {
          reportFullyDrawn()
          Sentry.reportFullyDisplayed()
        }
      }
    }
  }
}

/** A real non-candidate pager page: no navigation integration is composed in this Fragment. */
class Nav3PlainFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View =
    ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { Nav3SampleTheme { PlainNav3Screen("Plain page (3)") } }
    }
}

@Composable
internal fun Nav3OpenPlainActivityButton(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  Nav3SentryButton(
    label = "Plain Activity",
    onClick = { context.startActivity(Intent(context, Nav3PlainActivity::class.java)) },
    modifier = modifier,
  )
}

/**
 * A screen without a SentryNavEffect, for checking manually that no nav data or nav transaction
 * from a previously shown Nav3 root is attributed to it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlainNav3Screen(title: String, onBack: (() -> Unit)? = null) {
  val coroutineScope = rememberCoroutineScope()
  var snapshot by remember { mutableStateOf(PlainScreenScopeProbe.snapshot()) }
  var result by remember { mutableStateOf("") }
  LaunchedEffect(Unit) {
    while (true) {
      snapshot = PlainScreenScopeProbe.snapshot()
      delay(SNAPSHOT_REFRESH_MILLIS)
    }
  }
  Column(
    Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).semantics {
      testTagsAsResourceId = true
    }
  ) {
    Text(title, modifier = Modifier.testTag(nav3TestTag("plain_title")))
    Text(
      "This screen has no SentryNavEffect. The previous root's Navigation context should be absent."
    )
    Text(snapshot, modifier = Modifier.testTag(nav3TestTag("plain_scope_state")))
    Text(result, modifier = Modifier.testTag(nav3TestTag("plain_result")))
    TextButton(
      onClick = { coroutineScope.launch { result = PlainScreenScopeProbe.emitSpan() } },
      modifier = Modifier.testTag(nav3TestTag("plain_emit_span")),
    ) {
      Text("Emit span")
    }
    TextButton(
      onClick = { result = PlainScreenScopeProbe.captureEvent() },
      modifier = Modifier.testTag(nav3TestTag("plain_capture_event")),
    ) {
      Text("Capture event")
    }
    if (onBack != null) {
      TextButton(onClick = onBack, modifier = Modifier.testTag(nav3TestTag("plain_back"))) {
        Text("Back")
      }
    }
  }
}

/** Reads and exercises the scope from a plain screen. Main-thread only. */
private object PlainScreenScopeProbe {

  fun snapshot(): String {
    var state = ""
    Sentry.configureScope { scope ->
      val transaction = scope.transaction
      state =
        "Transaction: ${transaction?.name} (${transaction?.operation})\n" +
          "Screen: ${scope.screen}\n" +
          "Navigation: ${scope.contexts["Navigation"]}"
    }
    return state
  }

  suspend fun emitSpan(): String {
    val parent = Sentry.getSpan()
    val span = parent?.startChild("test.navigation.manual", "Nav3 plain screen span")
    // The span must finish even if the screen disappears during the delay.
    try {
      delay(EMITTED_SPAN_DURATION_MILLIS)
    } finally {
      span?.finish()
    }
    return record("Emitted span; parent=${parent?.spanContext?.spanId}; ${traceUrl(parent)}")
  }

  fun captureEvent(): String {
    val id = Sentry.captureMessage("Nav3 plain screen event")
    return record("Event: ${sentryEventSearchUrl(dsn, id.toString()) ?: id}")
  }

  private fun traceUrl(transaction: ISpan?): String {
    val traceId = transaction?.spanContext?.traceId?.toString() ?: return "No parent transaction"
    return sentryTraceUrl(dsn, traceId) ?: "Trace: $traceId"
  }

  private val dsn: String?
    get() = Sentry.getCurrentScopes().options.dsn

  private fun record(message: String): String {
    Log.i("Nav3PlainScreen", "$message\n${snapshot()}")
    return message
  }
}

private const val SNAPSHOT_REFRESH_MILLIS = 250L
private const val EMITTED_SPAN_DURATION_MILLIS = 300L

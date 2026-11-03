package io.sentry.samples.android.navigation.nav3

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.TransactionOptions
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.hasOnlyActivityUiLoadTransactions
import io.sentry.samples.android.navigation.common.showRouteWorkDialog
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
      setContent {
        Nav3SampleTheme {
          PlainNav3Screen(
            "Plain page (3)",
            navigationTools = {
              PlainPageNavigationTools(requireActivity() as Nav3FragmentsActivity)
            },
          )
        }
      }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlainNav3Screen(
  title: String,
  onBack: (() -> Unit)? = null,
  navigationTools: @Composable RowScope.() -> Unit = {},
) {
  val coroutineScope = rememberCoroutineScope()
  Column(
    Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).semantics {
      testTagsAsResourceId = true
    }
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(title, modifier = Modifier.weight(1f).testTag(nav3TestTag("plain_title")))
      navigationTools()
    }
    Text(
      "This screen has no SentryNavEffect. The previous root's Navigation context should be absent."
    )
    TextButton(
      onClick = { coroutineScope.launch { Nav3ScopeDiagnostics.emitSpan() } },
      modifier = Modifier.testTag(nav3TestTag("plain_emit_span")),
    ) {
      Text("Emit span")
    }
    TextButton(
      onClick = { Nav3ScopeDiagnostics.captureEvent() },
      modifier = Modifier.testTag(nav3TestTag("plain_capture_event")),
    ) {
      Text("Capture event")
    }
    Nav3DiagnosticsButton()
    if (onBack != null) {
      TextButton(onClick = onBack, modifier = Modifier.testTag(nav3TestTag("plain_back"))) {
        Text("Back")
      }
    }
  }
}

@Composable
private fun PlainPageNavigationTools(activity: Nav3FragmentsActivity) {
  var showHistory by remember { mutableStateOf(false) }
  var workOptions by remember { mutableStateOf(setOf(RouteWorkOption.MANUAL_CHILD_SPAN)) }
  val coroutineScope = rememberCoroutineScope()
  // Plain pages have no route changes: run selected work on resume and settings changes instead.
  LifecycleResumeEffect(workOptions) {
    if (RouteWorkOption.MANUAL_CHILD_SPAN in workOptions) {
      runManualNav3RouteActivationSpan("PlainPage3")
    }
    val work = coroutineScope.launch { runNav3RouteWork("PlainPage3", workOptions) }
    onPauseOrDispose { work.cancel() }
  }
  IconButton(
    onClick = { showHistory = true },
    modifier =
      Modifier.sentryTag(nav3InteractionTag("Recent Transactions"))
        .testTag(nav3TestTag("top_bar_recent_transactions")),
  ) {
    Icon(Icons.Filled.AccountTree, contentDescription = "Recent transactions")
  }
  IconButton(
    onClick = { showRouteWorkDialog(activity, workOptions) { workOptions = it } },
    modifier =
      Modifier.sentryTag(nav3InteractionTag("Route Work Settings"))
        .testTag(nav3TestTag("top_bar_route_work_settings")),
  ) {
    Icon(Icons.Filled.Settings, contentDescription = "Route work settings")
  }
  if (showHistory) {
    NavigationTransactionHistorySheet(
      sampleName = "Nav3",
      transactions = NavigationTransactionHistory.transactions,
      showActivityUiLoadTransactionDelayMessage =
        activity.configuration.hasOnlyActivityUiLoadTransactions,
      onDismissRequest = { showHistory = false },
      onOpenTransaction = { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
      onDumpTransactionUrl = {
        Log.i("Nav3PlainPage", "Sentry transaction URL: $it")
        Toast.makeText(activity, "Dumped transaction URL to logcat.", Toast.LENGTH_SHORT).show()
      },
      onCopyTransactionUrl = {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Sentry transaction URL", it))
        Toast.makeText(activity, "Copied transaction URL to clipboard.", Toast.LENGTH_SHORT).show()
      },
    )
  }
}

@Composable
internal fun Nav3DiagnosticsButton(modifier: Modifier = Modifier) {
  var open by remember { mutableStateOf(false) }
  TextButton(
    onClick = { open = true },
    modifier = modifier.testTag(nav3TestTag("diagnostics_open")),
  ) {
    Text("Diagnostics")
  }
  if (open) {
    Nav3DiagnosticsDialog { open = false }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav3DiagnosticsDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var snapshot by remember { mutableStateOf(Nav3ScopeDiagnostics.snapshot()) }
  var result by remember { mutableStateOf("") }
  LaunchedEffect(Unit) {
    while (true) {
      snapshot = Nav3ScopeDiagnostics.snapshot()
      delay(250)
    }
  }
  AlertDialog(
    modifier = Modifier.semantics { testTagsAsResourceId = true },
    onDismissRequest = onDismiss,
    title = { Text("Scope diagnostics") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(snapshot, modifier = Modifier.testTag(nav3TestTag("diagnostics_state")))
        Text(result, modifier = Modifier.testTag(nav3TestTag("diagnostics_result")))
        Text("Host transactions and held spans persist across screens. Finish them when done.")
        DiagnosticAction("Emit span", "emit_span") {
          coroutineScope.launch { result = Nav3ScopeDiagnostics.emitSpan() }
        }
        DiagnosticAction("Capture event", "capture_event") {
          result = Nav3ScopeDiagnostics.captureEvent()
        }
        DiagnosticAction("Start host transaction", "host_start") {
          result = Nav3ScopeDiagnostics.startHost()
        }
        DiagnosticAction("Finish host transaction", "host_finish") {
          result = Nav3ScopeDiagnostics.finishHost()
        }
        DiagnosticAction("Hold child span", "hold_span") {
          result = Nav3ScopeDiagnostics.holdSpan()
        }
        DiagnosticAction("Release child span", "release_span") {
          result = Nav3ScopeDiagnostics.releaseSpan()
        }
        DiagnosticAction("Open plain Activity", "open_plain_activity") {
          onDismiss()
          context.startActivity(Intent(context, Nav3PlainActivity::class.java))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag(nav3TestTag("diagnostics_close")),
      ) {
        Text("Close")
      }
    },
  )
}

@Composable
private fun DiagnosticAction(label: String, tag: String, onClick: () -> Unit) {
  TextButton(onClick = onClick, modifier = Modifier.testTag(nav3TestTag("diagnostics_$tag"))) {
    Text(label)
  }
}

/** Main-thread-only manual test controls. State survives page and Activity handoffs on purpose. */
private object Nav3ScopeDiagnostics {
  private var host: ITransaction? = null
  private var heldSpan: ISpan? = null

  fun snapshot(): String {
    var state = ""
    Sentry.configureScope { scope ->
      val transaction = scope.transaction
      state =
        "Transaction: ${transaction?.name}\n" +
          "Operation: ${transaction?.operation}\n" +
          "Trace: ${transaction?.spanContext?.traceId}\n" +
          "Finished: ${transaction?.isFinished}\n" +
          "Propagation trace: ${scope.propagationContext.traceId}\n" +
          "Screen: ${scope.screen}\n" +
          "Navigation: ${scope.contexts["Navigation"]}\n" +
          "Host: ${host?.name} (finished=${host?.isFinished})\n" +
          "Held span: ${heldSpan?.spanContext?.spanId}"
    }
    return state
  }

  suspend fun emitSpan(): String {
    val parent = Sentry.getSpan()
    val span = parent?.startChild("test.navigation.manual", "Nav3 diagnostics span")
    // The span must finish even if the dialog or screen disappears during the delay.
    try {
      delay(300)
    } finally {
      span?.finish()
    }
    return record("Emitted span; parent=${parent?.spanContext?.spanId}; ${traceUrl(parent)}")
  }

  fun captureEvent(): String {
    val id = Sentry.captureMessage("Nav3 scope diagnostics")
    return record("Event: https://sentry-sdks.sentry.io/issues/?project=5428559&query=$id")
  }

  fun startHost(): String {
    if (host?.isFinished == false) return record("Host transaction already running")
    // Explicitly replace the bound transaction to test host transaction precedence.
    host =
      Sentry.startTransaction(
        "Nav3DiagnosticsHost",
        "test.navigation.host",
        TransactionOptions().apply {
          deadlineTimeout = 60_000
        },
      )
    Sentry.configureScope { it.transaction = host }
    return record("Started host transaction; ${traceUrl(host)}")
  }

  fun finishHost(): String {
    val transaction = host
    transaction?.finish()
    Sentry.configureScope { scope ->
      scope.withTransaction { if (it === transaction) scope.clearTransaction() }
    }
    host = null
    return record("Finished host transaction; ${traceUrl(transaction)}")
  }

  fun holdSpan(): String {
    if (heldSpan?.isFinished == false) return record("A child span is already held")
    heldSpan = Sentry.getSpan()?.startChild("test.navigation.held", "Nav3 held child span")
    return record("Held span; parent trace=${heldSpan?.spanContext?.traceId}")
  }

  fun releaseSpan(): String {
    heldSpan?.finish()
    heldSpan = null
    return record("Released child span")
  }

  private fun traceUrl(transaction: ISpan?): String =
    transaction?.let {
      "https://sentry-sdks.sentry.io/explore/traces/trace/${it.spanContext.traceId}/?project=5428559"
    } ?: "No parent transaction"

  private fun record(message: String): String {
    Log.i("Nav3Diagnostics", "$message\n${snapshot()}")
    return message
  }
}

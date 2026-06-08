package io.sentry.samples.android.sqlite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.sentry.Sentry
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.SentryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SentryPink = Color(0xFFC85B9C)
private val SentryPurple = Color(0xFF7B52FB)
private val SentryRed = Color(0xFFF55459)

/** Intro text, surfaced via the "?" tooltip next to the "Run it" header. */
private const val INSTRUCTIONS =
  "Tap a button to execute a SQL statement in its own transaction; long press to run it in a ui.load transaction."

/** Start state of the "SQL run" box. */
private const val SQL_DETAIL_HINT = "Tap a button above to see the SQL it runs…"

private val TOGGLE_SECTION_GAP = 24.dp

private val CONTROL_SECTION_GAP = TOGGLE_SECTION_GAP * 2

private val SECTION_HEADER_HEIGHT = 28.dp

/** Which sentry-android-sqlite integration the demo buttons currently target. */
private enum class Integration(val color: Color, val apiName: String) {
  DRIVER(SentryPurple, "SQLiteDriver"),
  OPEN_HELPER(SentryPink, "SupportSQLiteOpenHelper"),
}

/**
 * How one demo button behaves for a given integration: which [SqlStatements] work it runs ([demo]),
 * the name/op of the manual transaction a tap wraps it in, and the SQL summary shown in the detail
 * panel ([displayInfo]).
 */
private class DemoVariant(
  val demo: SqlDemo,
  val transactionName: String,
  val op: String,
  val displayInfo: DisplayInfo,
)

/**
 * A single demo button in the list. [driver] / [openHelper] hold the variant for each integration;
 * a null variant means the row doesn't apply to that integration and renders dimmed, explaining why
 * on click (Room 3 is driver-only; SQLDelight is open-helper-only).
 */
private class DemoRow(val label: String, val driver: DemoVariant?, val openHelper: DemoVariant?)

// The demo buttons, top to bottom, paired with each integration's variant. Pure data — the actual
// SQL lives in SqlStatements, dispatched by id.
private val DEMO_ROWS =
  listOf(
    DemoRow(
      label = "Direct (no library)",
      driver =
        DemoVariant(
          demo = SqlDemo.DRIVER_DIRECT,
          transactionName = "SentrySQLiteDriver — Direct",
          op = "db.sql.driver-direct",
          displayInfo = DRIVER_DIRECT,
        ),
      openHelper =
        DemoVariant(
          demo = SqlDemo.OPENHELPER_DIRECT,
          transactionName = "SentrySupportSQLiteOpenHelper — Direct",
          op = "db.sql.openhelper-direct",
          displayInfo = OPENHELPER_DIRECT,
        ),
    ),
    DemoRow(
      label = "Room 2",
      driver =
        DemoVariant(
          demo = SqlDemo.DRIVER_ROOM2,
          transactionName = "SentrySQLiteDriver — Room 2",
          op = "db.sql.driver-room2",
          displayInfo = DRIVER_ROOM2,
        ),
      openHelper =
        DemoVariant(
          demo = SqlDemo.OPENHELPER_ROOM,
          transactionName = "SentrySupportSQLiteOpenHelper — Room",
          op = "db.sql.openhelper-room",
          displayInfo = OPENHELPER_ROOM,
        ),
    ),
    DemoRow(
      label = "Room 3",
      driver =
        DemoVariant(
          demo = SqlDemo.DRIVER_ROOM3,
          transactionName = "SentrySQLiteDriver — Room 3",
          op = "db.sql.driver-room3",
          displayInfo = DRIVER_ROOM3,
        ),
      openHelper = null, // Room 3 only runs on the SQLiteDriver path.
    ),
    DemoRow(
      label = "SQLDelight",
      driver = null, // SQLDelight's AndroidSqliteDriver is built on SupportSQLiteOpenHelper.
      openHelper =
        DemoVariant(
          demo = SqlDemo.OPENHELPER_SQLDELIGHT,
          transactionName = "SentrySupportSQLiteOpenHelper — SQLDelight",
          op = "db.sql.openhelper-sqldelight",
          displayInfo = OPENHELPER_SQLDELIGHT,
        ),
    ),
  )

/**
 * Activity that lets us exercise our two `sentry-android-sqlite` integrations
 * ([SentrySQLiteDriver][io.sentry.sqlite.SentrySQLiteDriver] and
 * [SentrySupportSQLiteOpenHelper][io.sentry.android.sqlite.SentrySupportSQLiteOpenHelper]), both
 * directly and via Room or SQLDelight.
 *
 * Example SQL statements are deliberately identical across integrations so we can identify
 * similarities and differences in their transaction / span support.
 */
class SQLiteActivity : ComponentActivity() {

  private var latestResult by mutableStateOf("")
  private var sqlDetail by mutableStateOf(SQL_DETAIL_HINT)
  private var heavyWork by mutableStateOf(false)

  /**
   * When enabled, every per-button transaction in one screen visit continues [screenTraceHeader],
   * so they all share a trace ("session"-like). When disabled (the default), each tap is the root
   * of its own trace, which renders as a standalone waterfall scaled to that one transaction —
   * easier to read how time is allocated among its spans.
   */
  private var shareScreenTrace by mutableStateOf(false)

  /** Which integration the demo buttons target. Switching it disables the rows that don't apply. */
  private var integration by mutableStateOf(Integration.DRIVER)

  /** Incremented on each tap that runs SQL. Used to retrigger the detail box's outline shimmer. */
  private var runTick by mutableStateOf(0)

  /** True while a demo or reset is running SQL on a background thread. */
  private var dbOperationInFlight by mutableStateOf(false)

  /** True for the duration of a reset; disables the reset button immediately (no debounce). */
  private var resetInProgress by mutableStateOf(false)

  /**
   * The shared trace used when [shareScreenTrace] is enabled: one trace per visit to this screen.
   * onResume() generates a fresh one each time the screen is (re)entered.
   */
  private var screenTraceHeader = newScreenTrace()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme {
        Surface {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            // A small gap below the screen title that grows with screen height and collapses to 0
            // on short screens, so the title isn't crowded against "Configure it" on tall devices.
            val titleGap =
              (((((screenHeightDp / 4) - 48) / 3).coerceAtLeast(0).dp + TOGGLE_SECTION_GAP) / 2 -
                  SECTION_HEADER_HEIGHT)
                .coerceAtLeast(0.dp)

            // Pulse the "Under the hood" outline in the integration color whenever a tap runs SQL.
            val shimmer = remember { Animatable(0f) }
            LaunchedEffect(runTick) {
              if (runTick == 0) return@LaunchedEffect
              shimmer.animateTo(
                targetValue = 0f,
                animationSpec =
                  keyframes {
                    durationMillis = 900
                    0f at 0
                    1f at 200
                    0.4f at 450
                    1f at 650
                    0f at 900
                  },
              )
            }

            val detailOutline =
              lerp(MaterialTheme.colorScheme.outline, integration.color, shimmer.value)

            Text(text = "SQLite Instrumentation", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(titleGap))

            SectionHeader("Configure it")

            val openHelper = integration == Integration.OPEN_HELPER
            val integrationSwitchColors =
              SwitchDefaults.colors(
                checkedTrackColor = SentryPink,
                checkedBorderColor = SentryPink,
                uncheckedTrackColor = SentryPurple,
                uncheckedBorderColor = SentryPurple,
                uncheckedThumbColor = Color.White,
              )
            val controlSwitchColors =
              SwitchDefaults.colors(
                checkedTrackColor = Color.Black,
                checkedBorderColor = Color.Black,
              )
            ToggleRow(
              label = if (openHelper) "SentrySupportSQLiteOpenHelper" else "SentrySQLiteDriver",
              checked = openHelper,
              labelColor = if (openHelper) SentryPink else SentryPurple,
              switchColors = integrationSwitchColors,
            ) {
              integration = if (it) Integration.OPEN_HELPER else Integration.DRIVER
              // Switching integration starts a fresh comparison: clear the detail box and result.
              sqlDetail = SQL_DETAIL_HINT
              latestResult = ""
            }
            ToggleRow(
              label = if (heavyWork) "Heavy app-level work" else "No app-level work",
              checked = heavyWork,
              switchColors = controlSwitchColors,
            ) {
              heavyWork = it
            }
            ToggleRow(
              label =
                if (shareScreenTrace) "Single trace for all button clicks"
                else "Separate trace per button click",
              checked = shareScreenTrace,
              switchColors = controlSwitchColors,
            ) {
              shareScreenTrace = it
            }

            SectionHeader("Run it", topPadding = CONTROL_SECTION_GAP) { HelpTooltip() }

            // One consolidated list of demo buttons. Each row dispatches to the selected
            // integration's variant; a row that doesn't apply explains why via a toast (see
            // [DemoRowButton]).
            DEMO_ROWS.forEach { row ->
              val variant = if (integration == Integration.DRIVER) row.driver else row.openHelper
              DemoRowButton(
                label = row.label,
                color = integration.color,
                variant = variant,
                disabledReason = "${row.label} doesn't use the ${integration.apiName}",
              )
            }

            ResetButton(
              dbOperationInFlight = dbOperationInFlight,
              resetInProgress = resetInProgress,
            )

            // Same [CONTROL_SECTION_GAP] above as the other sections, separating the controls from
            // the detail output.
            SectionHeader("Under the hood", topPadding = CONTROL_SECTION_GAP)
            // The latest run result (row counts, errors). Hidden until the first run.
            if (latestResult.isNotEmpty()) {
              Text(
                text = latestResult,
                style = MaterialTheme.typography.bodyMedium,
                color = if (latestResult.contains("failed")) SentryRed else Color.Unspecified,
              )
            }
            DetailField("SQL run", sqlDetail, borderColor = detailOutline)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // Start a new trace each time the user (re)enters the screen, so each visit is its own session.
    screenTraceHeader = newScreenTrace()
  }

  /** Run the variant's SQL statement inside a manual, scope-bound transaction. */
  private fun onTap(variant: DemoVariant) {
    if (dbOperationInFlight) return

    sqlDetail = if (heavyWork) variant.displayInfo.sqlHeavy else variant.displayInfo.sql
    runTick++ // shimmer the detail box outline in the integration color

    lifecycleScope.launch {
      dbOperationInFlight = true
      try {
        latestResult =
          withContext(Dispatchers.IO) {
            runInTransaction(variant.transactionName, variant.op) {
              SqlStatements.execute(applicationContext, variant.demo, heavyWork)
            }
          }
      } finally {
        dbOperationInFlight = false
      }
    }
  }

  /**
   * Run the variant's SQL statement in [UiLoadActivity] with no manual transaction, so its auto
   * `ui.load` transaction owns the spans.
   */
  private fun onLongPress(variant: DemoVariant) {
    if (dbOperationInFlight) return

    sqlDetail = if (heavyWork) variant.displayInfo.sqlHeavy else variant.displayInfo.sql
    latestResult = "Opened the auto-load screen — its ui.load transaction owns the db spans."
    startActivity(UiLoadActivity.intent(this, variant.demo, heavyWork))
  }

  /**
   * A compact, left-justified labeled switch. [labelColor] defaults to [Color.Unspecified] so the
   * label inherits the default text color; the integration toggle passes its pink/purple instead.
   */
  @androidx.compose.runtime.Composable
  private fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    switchColors: SwitchColors = SwitchDefaults.colors(),
    onCheckedChange: (Boolean) -> Unit,
  ) {
    // Constrain the row height: a Switch otherwise reserves ~48dp, leaving a large gap between the
    // toggles. 32dp keeps them about one line of text apart.
    Row(modifier = modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = switchColors,
        modifier = Modifier.scale(0.75f),
      )
      Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = labelColor,
        modifier = Modifier.padding(start = 4.dp),
      )
    }
  }

  @androidx.compose.runtime.Composable
  private fun SectionHeader(
    title: String,
    topPadding: Dp = 8.dp,
    trailing: (@androidx.compose.runtime.Composable () -> Unit)? = null,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = topPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
      }
      HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
    }
  }

  /**
   * A circled "?" next to the "Run it" header. Tapping it briefly shows the [INSTRUCTIONS] in a
   * tooltip that auto-dismisses after a few seconds.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @androidx.compose.runtime.Composable
  private fun HelpTooltip() {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(tooltipState.isVisible) {
      if (tooltipState.isVisible) {
        delay(4000)
        tooltipState.dismiss()
      }
    }
    TooltipBox(
      positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
      tooltip = { PlainTooltip { Text(INSTRUCTIONS) } },
      state = tooltipState,
    ) {
      Icon(
        imageVector = Icons.Outlined.HelpOutline,
        contentDescription = "What do the buttons do?",
        tint = Color.Gray,
        modifier =
          Modifier.padding(start = 8.dp).size(20.dp).clickable {
            scope.launch { tooltipState.show() }
          },
      )
    }
  }

  /**
   * A filled button that runs [variant] on tap (manual transaction) or long-press (ui.load). It's a
   * [Surface] rather than a [Button] because Material3's Button has no long-press hook; the
   * [combinedClickable] modifier gives us both.
   *
   * A null [variant] means the row doesn't apply to the selected integration: the button renders
   * dimmed and, when clicked, explains why via a toast ([disabledReason]) instead of running.
   */
  @OptIn(ExperimentalFoundationApi::class)
  @androidx.compose.runtime.Composable
  private fun DemoRowButton(
    label: String,
    color: Color,
    variant: DemoVariant?,
    disabledReason: String,
  ) {
    val context = LocalContext.current
    val enabled = variant != null
    val explain = { Toast.makeText(context, disabledReason, Toast.LENGTH_SHORT).show() }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = ButtonDefaults.shape,
      color = if (enabled) color else color.copy(alpha = 0.26f),
      contentColor = Color.White,
    ) {
      Box(
        modifier =
          Modifier.combinedClickable(
              onClick = { if (variant != null) onTap(variant) else explain() },
              onLongClick = { if (variant != null) onLongPress(variant) else explain() },
            )
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
      }
    }
  }

  @androidx.compose.runtime.Composable
  private fun ResetButton(dbOperationInFlight: Boolean, resetInProgress: Boolean) {
    // Debounce demo-driven disablement so fast taps don't flicker the button; reset disables
    // immediately via [resetInProgress]. [dbOperationInFlight] still guards [onClick] either way.
    var enabled by remember { mutableStateOf(true) }
    LaunchedEffect(dbOperationInFlight, resetInProgress) {
      when {
        resetInProgress -> enabled = false
        dbOperationInFlight -> {
          delay(RESET_DISABLE_DEBOUNCE_MS)
          enabled = false
        }
        else -> enabled = true
      }
    }

    Button(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      enabled = enabled,
      colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.White),
      onClick = {
        if (dbOperationInFlight) return@Button
        lifecycleScope.launch {
          this@SQLiteActivity.resetInProgress = true
          this@SQLiteActivity.dbOperationInFlight = true
          try {
            val message = withContext(Dispatchers.IO) { resetDatabases() }
            latestResult = message
            sqlDetail = "DROP: deletes every demo database file, resetting all row counts to 0."
          } finally {
            this@SQLiteActivity.dbOperationInFlight = false
            this@SQLiteActivity.resetInProgress = false
          }
        }
      },
    ) {
      Text("Drop all tables (reset)")
    }
  }

  @androidx.compose.runtime.Composable
  private fun DetailField(label: String, value: String, borderColor: Color) {
    OutlinedTextField(
      value = value,
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
      // The border color is driven by the shimmer animation so the box pulses on each SQL run.
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedBorderColor = borderColor,
          unfocusedBorderColor = borderColor,
        ),
      modifier = Modifier.fillMaxWidth(),
    )
  }

  /**
   * Runs [block] inside a scope-bound transaction and returns the result. When [shareScreenTrace]
   * is enabled, the transaction continues this screen's trace so all demos in one visit share a
   * trace; otherwise it starts its own trace (1 transaction = 1 trace).
   */
  private suspend fun runInTransaction(
    transactionName: String,
    op: String,
    block: suspend () -> String,
  ): String {
    // Continuing the screen trace keeps the shared trace id but mints a fresh span id for this
    // transaction; the standalone path (and the continueTrace fallback when tracing is disabled)
    // gives the transaction its own trace.
    val context =
      if (shareScreenTrace) {
        Sentry.continueTrace(screenTraceHeader, null)?.apply {
          name = transactionName
          operation = op
        } ?: TransactionContext(transactionName, op)
      } else {
        TransactionContext(transactionName, op)
      }

    val options = TransactionOptions().apply { isBindToScope = true }
    val transaction = Sentry.startTransaction(context, options)

    return try {
      val result = block()
      transaction.status = SpanStatus.OK
      result
    } catch (t: Throwable) {
      transaction.status = SpanStatus.INTERNAL_ERROR
      "$transactionName failed: ${t.message}"
    } finally {
      transaction.finish()
    }
  }

  /** Closes + deletes every demo database file (via [SampleDatabases]), then re-warms them. */
  private suspend fun resetDatabases(): String {
    val cleared = SampleDatabases.reset(applicationContext)
    return "Dropped tables: cleared $cleared database file(s)."
  }

  private companion object {

    /** Demo SQL shorter than this won't visibly disable the reset button. */
    private const val RESET_DISABLE_DEBOUNCE_MS = 300L

    /**
     * Builds a fresh sentry-trace header ("<traceId>-<spanId>-<sampled>") representing this screen
     * visit's trace. The trailing "-1" marks it sampled so the whole session is kept.
     */
    private fun newScreenTrace(): String = "${SentryId()}-${SpanId()}-1"
  }
}

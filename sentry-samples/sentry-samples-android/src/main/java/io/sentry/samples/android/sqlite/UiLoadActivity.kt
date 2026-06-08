package io.sentry.samples.android.sqlite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity that lets us simulate SDK auto-generation of a `ui.load` transaction + attach SQLite
 * statement spans to it.
 *
 * Timing note: the work runs off the main thread, so it finishes after the screen is first drawn.
 * Time-to-full-display tracing (enabled in the manifest) keeps the `ui.load` transaction open until
 * [Sentry.reportFullyDisplayed], which we call once the work completes — otherwise the transaction
 * would auto-finish at first display and the late db spans would have nowhere to attach.
 */
class UiLoadActivity : ComponentActivity() {

  private var status by mutableStateOf("Running under the screen's auto ui.load transaction…")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val id =
      SqlDemo.entries.find { it.name == intent.getStringExtra(EXTRA_DEMO_ID) }
        ?: run {
          finish()
          return
        }
    val heavy = intent.getBooleanExtra(EXTRA_HEAVY, false)

    setContent { UiLoadScreen(status = status, onClose = ::finish) }

    // No Sentry.startTransaction(): the work runs under the auto ui.load:UiLoadActivity span.
    lifecycleScope.launch {
      status =
        try {
          val result =
            withContext(Dispatchers.IO) { SqlStatements.execute(applicationContext, id, heavy) }
          "$result\n\nRan under the auto ui.load transaction."
        } catch (t: Throwable) {
          "Load failed: ${t.message}"
        } finally {
          // Close the TTFD window so the ui.load transaction finishes with the db spans attached.
          Sentry.reportFullyDisplayed()
        }
    }
  }

  companion object {
    private const val EXTRA_DEMO_ID = "demo_id"
    private const val EXTRA_HEAVY = "heavy"

    /** Builds the intent that runs [id] (honoring the [heavy] toggle) on this UiLoadScreen. */
    fun intent(context: Context, id: SqlDemo, heavy: Boolean): Intent =
      Intent(context, UiLoadActivity::class.java)
        .putExtra(EXTRA_DEMO_ID, id.name)
        .putExtra(EXTRA_HEAVY, heavy)
  }
}

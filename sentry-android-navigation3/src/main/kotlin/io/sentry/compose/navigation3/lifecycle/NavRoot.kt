package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle

/**
 * The nav root of a `SentryNavEffect`.
 *
 * A **nav root** is the Activity or Fragment whose content calls a `SentryNavEffect` and owns the
 * back stack the effect observes.
 */
internal class NavRoot(
  /** The nav root's lifecycle. */
  val lifecycle: Lifecycle,
  /**
   * The Activity that displays the nav root's content, or `null` if it's unknown.
   *
   * If the nav root is itself an Activity, this is the nav root. If the nav root is a Fragment,
   * this is the Activity that hosts the Fragment.
   */
  val activity: Activity?,
)

/**
 * The Activity the receiver belongs to, found by unwrapping [ContextWrapper]s (e.g., the
 * `ContextThemeWrapper` of a dialog), or `null` if there's none.
 */
internal fun Context.findActivity(): Activity? {
  var context: Context? = this
  while (context is ContextWrapper) {
    if (context is Activity) {
      return context
    }
    context = context.baseContext
  }
  return null
}

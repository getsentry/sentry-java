package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle

/**
 * Describes the nav root of a `SentryNavEffect`.
 *
 * A **nav root** is the Activity or Fragment whose content calls a `SentryNavEffect` and owns the
 * back stack the effect observes.
 */
internal class NavRootInfo(
  /** The nav root's lifecycle. */
  val lifecycle: Lifecycle,
  /** The context the nav root's content is displayed in, if known. */
  val context: Context?,
) {

  /**
   * The Activity that displays the nav root's content, or `null` if [context] is unknown or doesn't
   * belong to an Activity.
   *
   * If the nav root is itself an Activity, this will be the nav root itself. If the nav root is a
   * Fragment, this will be the Activity that hosts that Fragment.
   */
  val activity: Activity?
    get() = context?.findActivity()

  /**
   * The Activity the receiver belongs to, found by unwrapping [ContextWrapper]s (e.g., the
   * `ContextThemeWrapper` of a dialog), or `null` if there's none.
   */
  private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
      if (context is Activity) {
        return context
      }
      context = context.baseContext
    }
    return null
  }
}

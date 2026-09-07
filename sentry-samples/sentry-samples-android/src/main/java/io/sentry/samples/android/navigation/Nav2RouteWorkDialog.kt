package io.sentry.samples.android.navigation

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

internal fun showRouteWorkDialog(
  context: Context,
  selectedOptions: Set<RouteWorkOption>,
  onSelectedOptionsChanged: (Set<RouteWorkOption>) -> Unit,
) {
  val options = RouteWorkOption.entries.toTypedArray()
  val checkedItems = options.map { it in selectedOptions }.toBooleanArray()

  AlertDialog.Builder(context)
    .setCustomTitle(routeWorkDialogTitle(context))
    .setMultiChoiceItems(
      options.map { it.label }.toTypedArray(),
      checkedItems,
    ) { _, which, isChecked ->
      checkedItems[which] = isChecked
    }
    .setPositiveButton(android.R.string.ok) { _, _ ->
      onSelectedOptionsChanged(options.filterIndexed { index, _ -> checkedItems[index] }.toSet())
    }
    .setNegativeButton(android.R.string.cancel, null)
    .show()
}

private fun routeWorkDialogTitle(context: Context): View =
  LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(context.dp(24), context.dp(24), context.dp(24), 0)
    addView(
      TextView(context).apply {
        text = "Route work"
        textSize = 20f
        setTypeface(null, Typeface.BOLD)
        setTextColor(context.getColor(android.R.color.black))
      }
    )
    addView(
      TextView(context).apply {
        text = "Enable/disable the generation of spans by navigation destinations."
        textSize = 14f
        setTextColor(0xFF756E89.toInt())
        setPadding(0, context.dp(8), 0, 0)
      }
    )
  }

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

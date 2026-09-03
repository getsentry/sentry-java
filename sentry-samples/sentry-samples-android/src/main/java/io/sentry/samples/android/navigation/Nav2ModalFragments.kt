package io.sentry.samples.android.navigation

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import io.sentry.samples.android.R

class Nav2PromoDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity = requireActivity() as Nav2Activity
    activity.tagCurrentScenarioOnTransaction()
    val promoId = requireArguments().getString(Nav2Args.PROMO_ID).orEmpty()

    return Dialog(requireContext()).apply {
      window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      setContentView(
        LinearLayout(requireContext()).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(20.dp(requireContext()))
          addView(promoDialogContent(activity, promoId))
        }
      )
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)
  }

  private fun promoDialogContent(activity: Nav2Activity, promoId: String): View =
    LinearLayout(requireContext()).apply {
      val routeSpec = Nav2RouteSpecs.promoDialog
      orientation = LinearLayout.VERTICAL
      background = roundedSurface(context, topCornersOnly = false)
      setPadding(24.dp(context))
      addView(sectionLabel(context, "Navigation destination"))
      addView(titleText(context, routeSpec.title))
      routeSpec.description?.let { addView(bodyText(context, it)) }
      routeSpec.displayArguments(mapOf(Nav2Args.PROMO_ID to promoId)).firstOrNull()?.let {
        (label, value) ->
        addView(argumentPill(context, "$label=$value"))
      }
      addView(spacer(context, height = 16.dp(context)))
      addView(
        primaryButton(context, R.id.nav2_modal_capture_exception, "Exception") {
          activity.captureSampleException("Nav2")
        }
      )
      addView(spacer(context, height = 10.dp(context)))
      addView(
        secondaryButton(context, R.id.nav2_modal_crash_app, "Crash App") {
          activity.showCrashConfirmation("Nav2")
        }
      )
      addView(spacer(context, height = 10.dp(context)))
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.END
          layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
          addView(
            quietButton(context, R.id.nav2_modal_dismiss, "Dismiss") { activity.navigateBack() }
          )
        }
      )
    }
}

class Nav2ShareSheetFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity = requireActivity() as Nav2Activity
    activity.tagCurrentScenarioOnTransaction()
    val productId = requireArguments().getString(Nav2Args.PRODUCT_ID).orEmpty()

    return Dialog(requireContext()).apply {
      window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      setContentView(
        LinearLayout(requireContext()).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(12.dp(requireContext()), 0, 12.dp(requireContext()), 12.dp(requireContext()))
          addView(shareSheetContent(activity, productId))
        }
      )
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.apply {
      setLayout(MATCH_PARENT, WRAP_CONTENT)
      setGravity(Gravity.BOTTOM)
    }
  }

  private fun shareSheetContent(activity: Nav2Activity, productId: String): View =
    LinearLayout(requireContext()).apply {
      val routeSpec = Nav2RouteSpecs.shareSheet
      orientation = LinearLayout.VERTICAL
      setPadding(24.dp(context))
      background = roundedSurface(context, topCornersOnly = true)
      addView(sectionLabel(context, "Overlay surface"))
      addView(titleText(context, routeSpec.title))
      routeSpec.description?.let { addView(bodyText(context, it)) }
      routeSpec.displayArguments(mapOf(Nav2Args.PRODUCT_ID to productId)).firstOrNull()?.let {
        (label, value) ->
        addView(argumentPill(context, "$label=$value"))
      }
      addView(spacer(context, height = 16.dp(context)))
      addView(
        primaryButton(context, R.id.nav2_modal_capture_exception, "Capture Exception") {
          activity.captureSampleException("Nav2")
        }
      )
      addView(spacer(context, height = 10.dp(context)))
      addView(
        secondaryButton(context, R.id.nav2_modal_crash_app, "Crash App") {
          activity.showCrashConfirmation("Nav2")
        }
      )
      addView(spacer(context, height = 10.dp(context)))
      addView(
        primaryButton(context, R.id.nav2_share_sheet_done, "Done") { activity.navigateBack() }
          .apply {
            backgroundTintList = ColorStateList.valueOf(0xFFE8E1F7.toInt())
            setTextColor(0xFF4E4569.toInt())
          }
      )
    }
}

private fun sectionLabel(context: Context, textValue: String): TextView =
  TextView(context).apply {
    text = textValue.uppercase()
    textSize = 11f
    setTypeface(null, Typeface.BOLD)
    letterSpacing = 0.08f
    setTextColor(color(context, R.color.colorPrimary))
    setPadding(0, 0, 0, 10.dp(context))
  }

private fun titleText(context: Context, textValue: String): TextView =
  TextView(context).apply {
    text = textValue
    textSize = 26f
    setTypeface(null, Typeface.BOLD)
    setTextColor(color(context, android.R.color.black))
    setPadding(0, 0, 0, 8.dp(context))
  }

private fun bodyText(context: Context, textValue: String): TextView =
  TextView(context).apply {
    text = textValue
    textSize = 15f
    setTextColor(0xFF5E5873.toInt())
    setLineSpacing(0f, 1.12f)
    setPadding(0, 0, 0, 14.dp(context))
  }

private fun argumentPill(context: Context, textValue: String): TextView =
  TextView(context).apply {
    text = textValue
    textSize = 13f
    setTypeface(null, Typeface.BOLD)
    setTextColor(0xFF4E4569.toInt())
    background =
      GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0xFFF1E8FF.toInt())
        cornerRadius = 14.dp(context).toFloat()
      }
    setPadding(12.dp(context), 8.dp(context), 12.dp(context), 8.dp(context))
    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
  }

private fun primaryButton(context: Context, id: Int, label: String, onClick: () -> Unit): Button =
  Button(context).apply {
    this.id = id
    text = label
    isAllCaps = false
    backgroundTintList = ColorStateList.valueOf(color(context, R.color.colorPrimary))
    setTextColor(Color.WHITE)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
  }

private fun secondaryButton(context: Context, id: Int, label: String, onClick: () -> Unit): Button =
  Button(context).apply {
    this.id = id
    text = label
    isAllCaps = false
    backgroundTintList = ColorStateList.valueOf(color(context, R.color.colorAccent))
    setTextColor(Color.WHITE)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
  }

private fun quietButton(context: Context, id: Int, label: String, onClick: () -> Unit): Button =
  Button(context, null, android.R.attr.borderlessButtonStyle).apply {
    this.id = id
    text = label
    isAllCaps = false
    setTextColor(0xFF756E89.toInt())
    setOnClickListener { onClick() }
  }

private fun roundedSurface(context: Context, topCornersOnly: Boolean): GradientDrawable {
  return GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color(context, android.R.color.white))
    val radius = 28.dp(context).toFloat()
    if (topCornersOnly) {
      cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
    } else {
      cornerRadius = radius
    }
  }
}

private fun spacer(context: Context, height: Int): View =
  View(context).apply {
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, height)
  }

private fun color(context: Context, id: Int): Int = context.getColor(id)

private fun Int.dp(context: Context): Int =
  (this * context.resources.displayMetrics.density).toInt()

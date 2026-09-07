package io.sentry.samples.android.navigation

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView

internal class Nav2ContentHosts(
  context: Context,
  navHostId: Int,
  private val createComposeContent: () -> ComposeView,
  private val createPerformanceContent: () -> ComposeView,
) {

  private val contentContainer = FrameLayout(context)
  private val fragmentHostView =
    FrameLayout(context).apply {
      id = navHostId
      contentContainer.addView(this, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }
  private var composeNavHostView: ComposeView? = null
  private var performanceView: ComposeView? = null

  val view: View = contentContainer.apply {
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
  }

  fun showFragments() {
    fragmentHostView.visibility = View.VISIBLE
    removeComposeNavHostView()
    removePerformanceView()
  }

  fun showCompose() {
    fragmentHostView.visibility = View.GONE
    removePerformanceView()

    if (composeNavHostView == null) {
      composeNavHostView =
        createComposeContent().also { view ->
          contentContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }
  }

  fun showPerformance() {
    fragmentHostView.visibility = View.GONE
    removeComposeNavHostView()

    if (performanceView == null) {
      performanceView =
        createPerformanceContent().also { view ->
          contentContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }
  }

  fun removeComposeNavHostView() {
    composeNavHostView?.let { view ->
      disposeAndRemoveHostView(view)
      composeNavHostView = null
    }
  }

  fun removePerformanceView() {
    performanceView?.let { view ->
      disposeAndRemoveHostView(view)
      performanceView = null
    }
  }

  private fun disposeAndRemoveHostView(view: ComposeView) {
    view.disposeComposition()
    contentContainer.removeView(view)
  }
}

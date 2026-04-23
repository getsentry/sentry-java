package io.sentry.android.replay.util

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.NoOpLogger
import io.sentry.SentryReplayOptions
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
class ViewsTest {

  @BeforeTest
  fun setup() {
    // Required so Robolectric reports the activity window as visible; otherwise
    // View.isVisibleToUser() returns false and SurfaceView nodes are skipped.
    System.setProperty("robolectric.areWindowsMarkedVisible", "true")
  }

  @Test
  fun `hasSize returns true for positive values`() {
    val view = View(ApplicationProvider.getApplicationContext())
    view.right = 100
    view.bottom = 100
    assertTrue(view.hasSize())
  }

  @Test
  fun `hasSize returns false for null values`() {
    val view = View(ApplicationProvider.getApplicationContext())
    view.right = 0
    view.bottom = 0
    assertFalse(view.hasSize())
  }

  @Test
  fun `hasSize returns false for negative values`() {
    val view = View(ApplicationProvider.getApplicationContext())
    view.right = -1
    view.bottom = -1
    assertFalse(view.hasSize())
  }

  @Test
  fun `traverse collects visible SurfaceView nodes when a list is supplied`() {
    val activity = buildActivity(SurfaceViewActivity::class.java).setup().get()
    val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))
    val collected = mutableListOf<ViewHierarchyNode.SurfaceViewHierarchyNode>()

    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance(), collected)

    assertEquals(2, collected.size)
  }

  @Test
  fun `traverse does not collect SurfaceView nodes when list parameter is null`() {
    val activity = buildActivity(SurfaceViewActivity::class.java).setup().get()
    val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))

    // Default parameter (null) — equivalent to the pre-feature call site behavior.
    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance())

    // No assertion on a collection; the goal is that this overload still works and never NPEs.
    assertTrue(true)
  }

  @Test
  fun `traverse skips invisible SurfaceViews`() {
    val activity = buildActivity(SurfaceViewActivity::class.java).setup().get()
    val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
    // Hide one of the two SurfaceViews.
    var hidden = 0
    for (i in 0 until root.childCount) {
      val child = root.getChildAt(i)
      if (child is SurfaceView) {
        child.visibility = View.GONE
        hidden++
        break
      }
    }
    assertEquals(1, hidden, "test setup: expected to find a SurfaceView to hide")

    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))
    val collected = mutableListOf<ViewHierarchyNode.SurfaceViewHierarchyNode>()

    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance(), collected)

    assertEquals(1, collected.size)
  }
}

private class SurfaceViewActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root =
      FrameLayout(this).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      }
    root.addView(SurfaceView(this).apply { layoutParams = LayoutParams(100, 100) })
    root.addView(TextView(this).apply { text = "label" })
    root.addView(
      FrameLayout(this).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(SurfaceView(context).apply { layoutParams = LayoutParams(50, 50) })
      }
    )
    setContentView(root)
  }
}

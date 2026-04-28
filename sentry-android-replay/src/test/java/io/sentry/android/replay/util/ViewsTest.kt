package io.sentry.android.replay.util

import android.app.Activity
import android.os.Looper
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
import org.robolectric.Shadows.shadowOf

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
    val (root, _) = buildSurfaceViewHierarchy()
    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))
    val collected = mutableListOf<ViewHierarchyNode.SurfaceViewHierarchyNode>()

    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance(), collected)

    assertEquals(2, collected.size)
  }

  @Test
  fun `traverse does not collect SurfaceView nodes when list parameter is null`() {
    val (root, _) = buildSurfaceViewHierarchy()
    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))

    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance(), null)
  }

  @Test
  fun `traverse skips invisible SurfaceViews`() {
    val (root, surfaceViews) = buildSurfaceViewHierarchy()
    surfaceViews.first().visibility = View.GONE

    val rootNode = ViewHierarchyNode.fromView(root, null, 0, SentryReplayOptions(false, null))
    val collected = mutableListOf<ViewHierarchyNode.SurfaceViewHierarchyNode>()

    root.traverse(rootNode, SentryReplayOptions(false, null), NoOpLogger.getInstance(), collected)

    assertEquals(1, collected.size)
  }

  /**
   * Builds and attaches a small view tree: `FrameLayout(SurfaceView, TextView, FrameLayout(
   * SurfaceView))`. Returns the root [FrameLayout] and the two [SurfaceView]s in tree order so
   * tests can mutate visibility without re-walking the hierarchy.
   */
  private fun buildSurfaceViewHierarchy(): Pair<FrameLayout, List<SurfaceView>> {
    val activity = buildActivity(Activity::class.java).setup().get()
    val sv1 = SurfaceView(activity).apply { layoutParams = LayoutParams(100, 100) }
    val sv2 = SurfaceView(activity).apply { layoutParams = LayoutParams(50, 50) }
    val nested = FrameLayout(activity).apply { addView(sv2) }
    val root =
      FrameLayout(activity).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(sv1)
        addView(TextView(activity).apply { text = "label" })
        addView(nested)
      }
    activity.setContentView(root)
    // Flush the layout/attach pass so isAttachedToWindow / visibility computations are accurate.
    shadowOf(Looper.getMainLooper()).idle()
    return root to listOf(sv1, sv2)
  }
}

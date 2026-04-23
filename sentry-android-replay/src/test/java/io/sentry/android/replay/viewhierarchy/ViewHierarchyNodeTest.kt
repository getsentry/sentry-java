package io.sentry.android.replay.viewhierarchy

import android.view.SurfaceView
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryReplayOptions
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewHierarchyNodeTest {

  private val options = SentryReplayOptions(false, null)

  @Test
  fun `fromView returns SurfaceViewHierarchyNode for a SurfaceView`() {
    val surfaceView = SurfaceView(ApplicationProvider.getApplicationContext())

    val node = ViewHierarchyNode.fromView(surfaceView, null, 0, options)

    assertTrue(
      node is ViewHierarchyNode.SurfaceViewHierarchyNode,
      "expected SurfaceViewHierarchyNode but got ${node::class.simpleName}",
    )
    assertTrue(node.isImportantForContentCapture)
    assertSame(surfaceView, node.surfaceViewRef.get())
  }

  @Test
  fun `fromView returns GenericViewHierarchyNode for a plain View`() {
    val view = View(ApplicationProvider.getApplicationContext())

    val node = ViewHierarchyNode.fromView(view, null, 0, options)

    assertTrue(
      node is ViewHierarchyNode.GenericViewHierarchyNode,
      "expected GenericViewHierarchyNode but got ${node::class.simpleName}",
    )
  }
}

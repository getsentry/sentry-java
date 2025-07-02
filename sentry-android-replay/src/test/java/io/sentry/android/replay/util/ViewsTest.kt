package io.sentry.android.replay.util

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewsTest {
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
}

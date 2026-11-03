package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class NavRootInfoTest {

  private val lifecycle = mock<Lifecycle>()

  @Test
  fun `activity - is the context when the context is an activity`() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val root = NavRootInfo(lifecycle, context = activity)

    val found = root.activity

    assertThat(found).isSameInstanceAs(activity)
  }

  @Test
  fun `activity - is the wrapped activity when the context wraps an activity`() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val root = NavRootInfo(lifecycle, context = ContextWrapper(ContextThemeWrapper(activity, 0)))

    val found = root.activity

    assertThat(found).isSameInstanceAs(activity)
  }

  @Test
  fun `activity - is null when the context wraps no activity`() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val root = NavRootInfo(lifecycle, context)

    val found = root.activity

    assertThat(found).isNull()
  }

  @Test
  fun `activity - is null when the context isn't a context wrapper`() {
    val root = NavRootInfo(lifecycle, context = mock<Context>())

    val found = root.activity

    assertThat(found).isNull()
  }

  @Test
  fun `activity - is null when the context is null`() {
    val root = NavRootInfo(lifecycle, context = null)

    val found = root.activity

    assertThat(found).isNull()
  }
}

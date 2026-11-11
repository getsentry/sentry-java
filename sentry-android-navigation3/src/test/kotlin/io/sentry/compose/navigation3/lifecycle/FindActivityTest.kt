package io.sentry.compose.navigation3.lifecycle

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
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
class FindActivityTest {

  @Test
  fun `findActivity() - returns the context when the context is an activity`() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()

    val found = activity.findActivity()

    assertThat(found).isSameInstanceAs(activity)
  }

  @Test
  fun `findActivity() - returns the wrapped activity when the context wraps an activity`() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val context = ContextWrapper(ContextThemeWrapper(activity, 0))

    val found = context.findActivity()

    assertThat(found).isSameInstanceAs(activity)
  }

  @Test
  fun `findActivity() - returns null when the context wraps no activity`() {
    val context: Context = ApplicationProvider.getApplicationContext()

    val found = context.findActivity()

    assertThat(found).isNull()
  }

  @Test
  fun `findActivity() - returns null when the context isn't a context wrapper`() {
    val context = mock<Context>()

    val found = context.findActivity()

    assertThat(found).isNull()
  }
}

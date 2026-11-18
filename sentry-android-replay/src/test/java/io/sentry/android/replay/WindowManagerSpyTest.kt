package io.sentry.android.replay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class WindowManagerSpyTest {

  @Test
  fun `a swap that throws does not propagate`() {
    var swapped = false

    WindowManagerSpy.swapWindowManagerGlobalMViews {
      swapped = true
      error("listener failed")
    }

    assertThat(swapped).isTrue()
  }

  @Test
  fun `a swap that throws a fatal error rethrows it`() {
    assertFailsWith<OutOfMemoryError> {
      WindowManagerSpy.swapWindowManagerGlobalMViews { throw OutOfMemoryError("listener") }
    }
  }
}

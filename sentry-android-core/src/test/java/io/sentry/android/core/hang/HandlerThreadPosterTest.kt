package io.sentry.android.core.hang

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HandlerThreadPosterTest {

  private val sut = HandlerThreadPoster(Handler(Looper.getMainLooper()))

  @Test
  fun `post runs the runnable on the main looper`() {
    var ranOn: Thread? = null

    sut.post { ranOn = Thread.currentThread() }
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(ranOn).isSameInstanceAs(Looper.getMainLooper().thread)
  }

  @Test
  fun `remove drops only the given pending runnable`() {
    val ran = mutableListOf<String>()
    val removed = Runnable { ran.add("removed") }
    val kept = Runnable { ran.add("kept") }
    sut.post(removed)
    sut.post(kept)

    sut.remove(removed)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(ran).containsExactly("kept")
  }
}

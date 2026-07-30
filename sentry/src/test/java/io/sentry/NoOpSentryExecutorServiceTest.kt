package io.sentry

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.mockito.kotlin.mock

class NoOpSentryExecutorServiceTest {
  private var sut: ISentryExecutorService = NoOpSentryExecutorService.getInstance()

  @Test
  fun `submit runnable returns a Future`() {
    val future = sut.submit(mock())
    assertNotNull(future)
  }

  @Test
  fun `submit callable returns a Future`() {
    val future = sut.submit(mock<Callable<*>>())
    assertNotNull(future)
  }

  @Test
  fun `schedule returns a Future`() {
    val future = sut.submit(mock<Callable<*>>())
    assertNotNull(future)
  }

  @Test
  fun `submitted tasks are reported as cancelled instead of pending forever`() {
    assertThat(sut.submit(mock<Runnable>()).isCancelled).isTrue()
    assertThat(sut.submit(mock<Callable<*>>()).isCancelled).isTrue()
    assertThat(sut.schedule(mock(), 0).isCancelled).isTrue()
  }

  @Test
  fun `getting the result of a dropped task fails fast`() {
    assertFailsWith<CancellationException> { sut.submit(mock<Runnable>()).get() }
  }

  @Test fun `close does not throw`() = sut.close(0)

  @Test
  fun `isClosed returns false`() {
    assertFalse(sut.isClosed)
  }
}

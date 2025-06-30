package io.sentry

import java.util.concurrent.Callable
import kotlin.test.Test
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

  @Test fun `close does not throw`() = sut.close(0)

  @Test
  fun `isClosed returns false`() {
    assertFalse(sut.isClosed)
  }
}

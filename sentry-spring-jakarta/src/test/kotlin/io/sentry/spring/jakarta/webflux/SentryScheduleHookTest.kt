package io.sentry.spring.jakarta.webflux

import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.test.initForTest
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await

class SentryScheduleHookTest {
  private val dsn = "http://key@localhost/proj"
  private lateinit var executor: ExecutorService

  private fun applyHookFromChildScopes(): Pair<Runnable, WeakReference<IScopes>> {
    val childScopes = Sentry.getCurrentScopes().forkedCurrentScope("child")
    val wrappedRunnable = childScopes.makeCurrent().use { SentryScheduleHook().apply(Runnable {}) }
    return wrappedRunnable to WeakReference(childScopes)
  }

  @BeforeTest
  fun beforeTest() {
    executor = Executors.newSingleThreadExecutor()
  }

  @AfterTest
  fun afterTest() {
    Sentry.close()
    executor.shutdown()
  }

  @Test
  fun `scopes is reset to its state within the thread after hook is done`() {
    initForTest { it.dsn = dsn }

    val sut = SentryScheduleHook()

    val mainScopes = Sentry.getCurrentScopes()
    val threadedScopes = Sentry.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { Sentry.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, Sentry.getCurrentScopes())

    val callableFuture =
      executor.submit(
        sut.apply {
          assertNotEquals(mainScopes, Sentry.getCurrentScopes())
          assertNotEquals(threadedScopes, Sentry.getCurrentScopes())
        }
      )

    callableFuture.get()

    executor
      .submit {
        assertNotEquals(mainScopes, Sentry.getCurrentScopes())
        assertEquals(threadedScopes, Sentry.getCurrentScopes())
      }
      .get()
  }

  @Test
  fun `wrapped task does not strongly retain scheduling scopes`() {
    initForTest { it.dsn = dsn }
    val (wrappedRunnable, childReference) = applyHookFromChildScopes()

    await.atMost(5, TimeUnit.SECONDS).untilAsserted {
      System.gc()
      System.runFinalization()
      assertNull(childReference.get())
    }

    wrappedRunnable.run()
  }

  @Test
  fun `known maintenance runnable class names are excluded`() {
    val classNames =
      listOf(
        "reactor.kafka.receiver.internals.ConsumerEventLoop\$PollEvent",
        "reactor.netty.internal.shaded.reactor.pool.SimpleDequePool\$\$Lambda\$42/0x00000001",
        "reactor.netty.internal.shaded.reactor.pool.SimpleDequePool\$\$Lambda/0x00000001",
        "reactor.netty.http.client.Http2Pool\$\$Lambda\$42/0x00000001",
        "reactor.netty.http.client.Http2Pool\$\$Lambda/0x00000001",
        "reactor.netty.resources.PooledConnectionProvider\$\$Lambda\$42/0x00000001",
        "reactor.netty.resources.PooledConnectionProvider\$\$Lambda/0x00000001",
      )

    classNames.forEach { className ->
      assertTrue(SentryScheduleHook.isExcluded(className), className)
    }
  }

  @Test
  fun `request carrying runnable class names are not excluded`() {
    val classNames =
      listOf(
        "reactor.netty.http.client.Http2Pool\$Borrower",
        "reactor.netty.internal.shaded.reactor.pool.SimpleDequePool\$QueueBorrowerMono",
        "reactor.pool.SimpleDequePool\$\$Lambda\$42/0x00000001",
        "com.example.RequestTask",
      )

    classNames.forEach { className ->
      assertFalse(SentryScheduleHook.isExcluded(className), className)
    }
  }
}

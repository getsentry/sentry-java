package io.sentry.spring

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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.awaitility.kotlin.await

class SentryTaskDecoratorTest {
  private val dsn = "http://key@localhost/proj"
  private lateinit var executor: ExecutorService

  private fun decorateFromChildScopes(): Pair<Runnable, WeakReference<IScopes>> {
    val childScopes = Sentry.getCurrentScopes().forkedCurrentScope("child")
    val decoratedRunnable =
      childScopes.makeCurrent().use { SentryTaskDecorator().decorate(Runnable {}) }
    return decoratedRunnable to WeakReference(childScopes)
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
  fun `decorated task does not strongly retain scheduling scopes`() {
    initForTest { it.dsn = dsn }
    val (decoratedRunnable, childReference) = decorateFromChildScopes()

    await.atMost(5, TimeUnit.SECONDS).untilAsserted {
      System.gc()
      System.runFinalization()
      assertNull(childReference.get())
    }

    decoratedRunnable.run()
  }

  @Test
  fun `scopes is reset to its state within the thread after decoration is done`() {
    initForTest { it.dsn = dsn }

    val sut = SentryTaskDecorator()

    val mainScopes = Sentry.getCurrentScopes()
    val threadedScopes = Sentry.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { Sentry.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, Sentry.getCurrentScopes())

    val callableFuture =
      executor.submit(
        sut.decorate {
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
}

package io.sentry.spring

import io.sentry.Sentry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SentryTaskDecoratorTest {
    private val dsn = "http://key@localhost/proj"
    private lateinit var executor: ExecutorService

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
    fun `hub is reset to its state within the thread after decoration is done`() {
        Sentry.init {
            it.dsn = dsn
        }

        val sut = SentryTaskDecorator()

        val mainHub = Sentry.getCurrentScopes()
        val threadedHub = Sentry.getCurrentScopes().clone()

        executor.submit {
            Sentry.setCurrentScopes(threadedHub)
        }.get()

        assertEquals(mainHub, Sentry.getCurrentScopes())

        val callableFuture =
            executor.submit(
                sut.decorate {
                    assertNotEquals(mainHub, Sentry.getCurrentScopes())
                    assertNotEquals(threadedHub, Sentry.getCurrentScopes())
                }
            )

        callableFuture.get()

        executor.submit {
            assertNotEquals(mainHub, Sentry.getCurrentScopes())
            assertEquals(threadedHub, Sentry.getCurrentScopes())
        }.get()
    }
}

package io.sentry.spring

import io.sentry.Sentry
import io.sentry.test.initForTest
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
    fun `scopes is reset to its state within the thread after decoration is done`() {
        initForTest {
            it.dsn = dsn
        }

        val sut = SentryTaskDecorator()

        val mainScopes = Sentry.getCurrentScopes()
        val threadedScopes = Sentry.getCurrentScopes().forkedCurrentScope("test")

        executor
            .submit {
                Sentry.setCurrentScopes(threadedScopes)
            }.get()

        assertEquals(mainScopes, Sentry.getCurrentScopes())

        val callableFuture =
            executor.submit(
                sut.decorate {
                    assertNotEquals(mainScopes, Sentry.getCurrentScopes())
                    assertNotEquals(threadedScopes, Sentry.getCurrentScopes())
                },
            )

        callableFuture.get()

        executor
            .submit {
                assertNotEquals(mainScopes, Sentry.getCurrentScopes())
                assertEquals(threadedScopes, Sentry.getCurrentScopes())
            }.get()
    }
}

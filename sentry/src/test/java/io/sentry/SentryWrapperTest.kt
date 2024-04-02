package io.sentry

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SentryWrapperTest {

    private val dsn = "http://key@localhost/proj"
    private lateinit var executor: ExecutorService

    @BeforeTest
    fun beforeTest() {
        executor = Executors.newSingleThreadExecutor()
    }

    @AfterTest
    fun afterTest() {
        executor.shutdown()
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `hub is reset to its state within the thread after supply is done`() {
        Sentry.init {
            it.dsn = dsn
            it.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                event
            }
        }

        val mainHub = Sentry.getCurrentScopes()
        val threadedHub = Sentry.getCurrentScopes().clone()

        executor.submit {
            Sentry.setCurrentScopes(threadedHub)
        }.get()

        assertEquals(mainHub, Sentry.getCurrentScopes())

        val callableFuture =
            CompletableFuture.supplyAsync(
                SentryWrapper.wrapSupplier {
                    assertNotEquals(mainHub, Sentry.getCurrentScopes())
                    assertNotEquals(threadedHub, Sentry.getCurrentScopes())
                    "Result 1"
                },
                executor
            )

        callableFuture.join()

        executor.submit {
            assertNotEquals(mainHub, Sentry.getCurrentScopes())
            assertEquals(threadedHub, Sentry.getCurrentScopes())
        }.get()
    }

    @Test
    fun `wrapped supply async isolates Hubs`() {
        val capturedEvents = mutableListOf<SentryEvent>()

        Sentry.init {
            it.dsn = dsn
            it.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                capturedEvents.add(event)
                event
            }
        }

        Sentry.addBreadcrumb("MyOriginalBreadcrumbBefore")
        Sentry.captureMessage("OriginalMessageBefore")

        val callableFuture =
            CompletableFuture.supplyAsync(
                SentryWrapper.wrapSupplier {
                    Thread.sleep(20)
                    Sentry.addBreadcrumb("MyClonedBreadcrumb")
                    Sentry.captureMessage("ClonedMessage")
                    "Result 1"
                },
                executor
            )

        val callableFuture2 =
            CompletableFuture.supplyAsync(
                SentryWrapper.wrapSupplier {
                    Thread.sleep(10)
                    Sentry.addBreadcrumb("MyClonedBreadcrumb2")
                    Sentry.captureMessage("ClonedMessage2")
                    "Result 2"
                },
                executor
            )

        Sentry.addBreadcrumb("MyOriginalBreadcrumb")
        Sentry.captureMessage("OriginalMessage")

        callableFuture.join()
        callableFuture2.join()

        val mainEvent = capturedEvents.firstOrNull { it.message?.formatted == "OriginalMessage" }
        val clonedEvent = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage" }
        val clonedEvent2 = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage2" }

        assertEquals(2, mainEvent?.breadcrumbs?.size)
        assertEquals(2, clonedEvent?.breadcrumbs?.size)
        assertEquals(2, clonedEvent2?.breadcrumbs?.size)
    }

    @Test
    fun `wrapped callable isolates Hubs`() {
        val capturedEvents = mutableListOf<SentryEvent>()

        Sentry.init {
            it.dsn = dsn
            it.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                capturedEvents.add(event)
                event
            }
        }

        Sentry.addBreadcrumb("MyOriginalBreadcrumbBefore")
        Sentry.captureMessage("OriginalMessageBefore")
        println(Thread.currentThread().name)

        val future1 = executor.submit(
            SentryWrapper.wrapCallable {
                Thread.sleep(20)
                Sentry.addBreadcrumb("MyClonedBreadcrumb")
                Sentry.captureMessage("ClonedMessage")
                "Result 1"
            }
        )

        val future2 = executor.submit(
            SentryWrapper.wrapCallable {
                Thread.sleep(10)
                Sentry.addBreadcrumb("MyClonedBreadcrumb2")
                Sentry.captureMessage("ClonedMessage2")
                "Result 2"
            }
        )

        Sentry.addBreadcrumb("MyOriginalBreadcrumb")
        Sentry.captureMessage("OriginalMessage")

        future1.get()
        future2.get()

        val mainEvent = capturedEvents.firstOrNull { it.message?.formatted == "OriginalMessage" }
        val clonedEvent = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage" }
        val clonedEvent2 = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage2" }

        assertEquals(2, mainEvent?.breadcrumbs?.size)
        assertEquals(2, clonedEvent?.breadcrumbs?.size)
        assertEquals(2, clonedEvent2?.breadcrumbs?.size)
    }

    @Test
    fun `hub is reset to its state within the thread after callable is done`() {
        Sentry.init {
            it.dsn = dsn
        }

        val mainHub = Sentry.getCurrentScopes()
        val threadedHub = Sentry.getCurrentScopes().clone()

        executor.submit {
            Sentry.setCurrentScopes(threadedHub)
        }.get()

        assertEquals(mainHub, Sentry.getCurrentScopes())

        val callableFuture =
            executor.submit(
                SentryWrapper.wrapCallable {
                    assertNotEquals(mainHub, Sentry.getCurrentScopes())
                    assertNotEquals(threadedHub, Sentry.getCurrentScopes())
                    "Result 1"
                }
            )

        callableFuture.get()

        executor.submit {
            assertNotEquals(mainHub, Sentry.getCurrentScopes())
            assertEquals(threadedHub, Sentry.getCurrentScopes())
        }.get()
    }
}

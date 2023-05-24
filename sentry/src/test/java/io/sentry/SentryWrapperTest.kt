package io.sentry

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryWrapperTest {

    private val dsn = "http://key@localhost/proj"
    private val executor = Executors.newSingleThreadExecutor()

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
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
                    Sentry.addBreadcrumb("MyClonedBreadcrumb")
                    Sentry.captureMessage("ClonedMessage")
                    "Result 1"
                },
                executor
            )

        val callableFuture2 =
            CompletableFuture.supplyAsync(
                SentryWrapper.wrapSupplier {
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
                Sentry.addBreadcrumb("MyClonedBreadcrumb")
                Sentry.captureMessage("ClonedMessage")
                "Result 1"
            }
        )

        val future2 = executor.submit(
            SentryWrapper.wrapCallable {
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
}

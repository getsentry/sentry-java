package io.sentry

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SentryWrapperTest {

    private val dsn = "http://key@localhost/proj"

    @BeforeTest
    @AfterTest
    fun beforeTest() {
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    @Test
    fun `wrap callable`() {
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
        println("Hub before ${Sentry.getCurrentHub()}")
        println(Thread.currentThread().name)
        val callableFuture =
            CompletableFuture.supplyAsync(
                SentryWrapper.wrapSupplier {
                    println(Thread.currentThread().name)
                    try {
                        TimeUnit.SECONDS.sleep(4)
                    } catch (e: InterruptedException) {
                        throw IllegalStateException(e)
                    }
                    Sentry.addBreadcrumb("MyClonedBreadcrumb")
                    Sentry.captureMessage("ClonedMessage")

                    System.out.println("After Future")
                    "Result of the asynchronous computation"
                }
            )

//        val callableFuture =
//            SentryWrapper.wrapCallable {
//            CompletableFuture.supplyAsync {
//                println(Thread.currentThread().name)
// //                println("Hub in supply Async ${Sentry.getCurrentHub()}")
//                    try {
//                        TimeUnit.SECONDS.sleep(4)
//                    } catch (e: InterruptedException) {
//                        throw IllegalStateException(e)
//                    }
// //                println("Hub in supply Async ${Sentry.getCurrentHub()}")
//                Sentry.addBreadcrumb("MyClonedBreadcrumb")
//                Sentry.captureMessage("ClonedMessage")
//                println(Sentry.getCurrentHub())
//
//                System.out.println("After Future")
//                "Result of the asynchronous computation"
//            }
//            }.call()
//        System.out.println("Before Callable")
//        val resultCallable = callableFuture.
//        System.out.println("After Callable")
//        try {
//            System.out.println("Before Sleep")
//            TimeUnit.SECONDS.sleep(4)
//            System.out.println("After Sleep")
//        } catch (e: InterruptedException) {
//            throw IllegalStateException(e)
//        }

        println("MainThread: " + Thread.currentThread().name)

        Callable {
            println("Callable Thread: " + Thread.currentThread().name)
        }.call()

        println("Hub after sleep ${Sentry.getCurrentHub()}")
        Sentry.addBreadcrumb("MyOriginalBreadcrumb")
        Sentry.captureMessage("OriginalMessage")

        val result = callableFuture.join()

        Sentry.addBreadcrumb("MyOriginalBreadcrumbAfterFutureCompletion")
        Sentry.captureMessage("MyOriginalMessageAfterFutureCompletion")

        println("Hub after get ${Sentry.getCurrentHub()}")
        val mainCloneEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageMainClone" }
        val currentHubEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageCurrent" }

        println(result)
    }
}

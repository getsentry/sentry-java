package io.sentry.clientreport

import io.sentry.DataCategory
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryOptions
import io.sentry.dsnString
import io.sentry.protocol.SentryId
import io.sentry.test.initForTest
import java.util.UUID
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientReportMultiThreadingTest {

    lateinit var opts: SentryOptions
    val reasons = DiscardReason.values()
    val categories = listOf(DataCategory.Error, DataCategory.Attachment, DataCategory.Session, DataCategory.Transaction, DataCategory.UserReport)

    @Test
    fun testMultiThreadedCountIncrements() {
        setupSentry()

        val clientReportRecorder = ClientReportRecorder(opts)

        val numberOfIncrementThreads = 10
        val numberOfIncrementsPerThread = 10 * 1000

        val executor = Executors.newFixedThreadPool(numberOfIncrementThreads)
        val completionService = ExecutorCompletionService<Unit>(executor)

        println("forking from thread ${Thread.currentThread()}")

        val futures = mutableListOf<Future<Unit>>()

        val t1 = System.currentTimeMillis()

        (1..numberOfIncrementThreads).forEach { nThread ->
            futures.add(
                completionService.submit {
                    println("running #$nThread on thread ${Thread.currentThread()}")

                    (1..numberOfIncrementsPerThread).forEach {
                        clientReportRecorder.recordLostEvent(randomReason(), randomCategory())
                    }
                }
            )
        }

        futures.forEach {
            completionService.take().get()
        }

        val t2 = System.currentTimeMillis()
        println("took ${t2 - t1}ms")

        val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
        val numberOfLostItems = clientReport?.discardedEvents?.sumOf { it.quantity.toInt() } ?: 0L

        assertEquals(numberOfIncrementThreads * numberOfIncrementsPerThread, numberOfLostItems)
    }

    @Test
    fun testMultiThreadedCountIncrementsAndResets() {
        setupSentry()

        val clientReportRecorder = ClientReportRecorder(opts)

        val numberOfIncrementThreads = 10
        val numberOfIncrementsPerThread = 10 * 1000
        val numberOfResets = 50

        val executor = Executors.newFixedThreadPool(numberOfIncrementThreads + 1)
        val completionService = ExecutorCompletionService<Unit>(executor)

        println("forking from thread ${Thread.currentThread()}")

        val futures = mutableListOf<Future<Unit>>()
        val clientReports = mutableListOf<ClientReport>()

        val t1 = System.currentTimeMillis()

        (1..numberOfIncrementThreads).forEach { nThread ->
            futures.add(
                completionService.submit {
                    println("running #$nThread on thread ${Thread.currentThread()}")

                    (1..numberOfIncrementsPerThread).forEach {
                        clientReportRecorder.recordLostEvent(randomReason(), randomCategory())
                    }
                }
            )
        }

        futures.add(
            completionService.submit {
                println("reset loop running on thread ${Thread.currentThread()}")

                (1..numberOfResets).forEach {
                    clientReportRecorder.resetCountsAndGenerateClientReport()?.let { clientReports.add(it) }
                    Thread.sleep(20)
                }
            }
        )

        futures.forEach {
            completionService.take().get()
        }

        val t2 = System.currentTimeMillis()
        println("took ${t2 - t1}ms")

        clientReportRecorder.resetCountsAndGenerateClientReport()?.let { clientReports.add(it) }
        val numberOfLostItems = clientReports.sumOf { clientReport -> clientReport.discardedEvents.sumOf { it.quantity.toInt() } }

        assertEquals(numberOfIncrementThreads * numberOfIncrementsPerThread, numberOfLostItems)
    }

    @Test
    fun testMultiThreadedCountIncrementsResetsAndReadds() {
        setupSentry()

        val clientReportRecorder = ClientReportRecorder(opts)

        val numberOfIncrementThreads = 10
        val numberOfIncrementsPerThread = 10 * 1000
        val numberOfResets = 50

        val executor = Executors.newFixedThreadPool(numberOfIncrementThreads + 1)
        val completionService = ExecutorCompletionService<Unit>(executor)

        println("forking from thread ${Thread.currentThread()}")

        val futures = mutableListOf<Future<Unit>>()

        val t1 = System.currentTimeMillis()

        (1..numberOfIncrementThreads).forEach { nThread ->
            futures.add(
                completionService.submit {
                    println("running #$nThread on thread ${Thread.currentThread()}")

                    (1..numberOfIncrementsPerThread).forEach {
                        clientReportRecorder.recordLostEvent(randomReason(), randomCategory())
                    }
                }
            )
        }

        futures.add(
            completionService.submit {
                println("reset loop running on thread ${Thread.currentThread()}")

                (1..numberOfResets).forEach {
                    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
                    Thread.sleep(20)
                    clientReport?.let { clientReport ->
                        val envelopeItem = SentryEnvelopeItem.fromClientReport(opts.serializer, clientReport)
                        val header = SentryEnvelopeHeader(SentryId(UUID.randomUUID()))
                        val envelope = SentryEnvelope(header, listOf(envelopeItem))
                        clientReportRecorder.recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope)
                    }
                }
            }
        )

        futures.forEach {
            completionService.take().get()
        }

        val t2 = System.currentTimeMillis()
        println("took ${t2 - t1}ms")

        val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
        val numberOfLostItems = clientReport?.discardedEvents?.sumOf { it.quantity.toInt() } ?: 0L

        assertEquals(numberOfIncrementThreads * numberOfIncrementsPerThread, numberOfLostItems)
    }

    private fun setupSentry(callback: Sentry.OptionsConfiguration<SentryOptions>? = null) {
        initForTest { options ->
            options.dsn = dsnString
            callback?.configure(options)
            opts = options
        }
    }

    private fun randomCategory(): DataCategory {
        return categories.random()
    }

    private fun randomReason(): DiscardReason {
        return reasons.random()
    }
}

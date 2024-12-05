package io.sentry.clientreport

import io.sentry.Attachment
import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.DataCategory
import io.sentry.DateUtils
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.NoOpLogger
import io.sentry.ProfilingTraceData
import io.sentry.ReplayRecording
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryReplayEvent
import io.sentry.SentryTracer
import io.sentry.Session
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.sentry.UserFeedback
import io.sentry.dsnString
import io.sentry.hints.Retryable
import io.sentry.metrics.EncodedMetrics
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientReportTest {

    lateinit var opts: SentryOptions
    lateinit var clientReportRecorder: ClientReportRecorder
    lateinit var testHelper: ClientReportTestHelper

    @Test
    fun `lost envelope can be recorded`() {
        givenClientReportRecorder()
        val hub = mock<IHub>()
        whenever(hub.options).thenReturn(opts)
        val transaction = SentryTracer(TransactionContext("name", "op"), hub)

        val lostClientReport = ClientReport(
            DateUtils.getCurrentDateTime(),
            listOf(
                DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 3),
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 2),
                DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.Transaction.category, 1)
            )
        )

        val envelope = testHelper.newEnvelope(
            SentryEnvelopeItem.fromClientReport(opts.serializer, lostClientReport),
            SentryEnvelopeItem.fromEvent(opts.serializer, SentryTransaction(transaction)),
            SentryEnvelopeItem.fromEvent(opts.serializer, SentryEvent()),
            SentryEnvelopeItem.fromSession(opts.serializer, Session("dis", User(), "env", "0.0.1")),
            SentryEnvelopeItem.fromUserFeedback(opts.serializer, UserFeedback(SentryId(UUID.randomUUID()))),
            SentryEnvelopeItem.fromAttachment(opts.serializer, NoOpLogger.getInstance(), Attachment("{ \"number\": 10 }".toByteArray(), "log.json"), 1000),
            SentryEnvelopeItem.fromProfilingTrace(ProfilingTraceData(File(""), transaction), 1000, opts.serializer),
            SentryEnvelopeItem.fromCheckIn(opts.serializer, CheckIn("monitor-slug-1", CheckInStatus.ERROR)),
            SentryEnvelopeItem.fromMetrics(EncodedMetrics(emptyMap())),
            SentryEnvelopeItem.fromReplay(opts.serializer, opts.logger, SentryReplayEvent(), ReplayRecording(), false)
        )

        clientReportRecorder.recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope)

        val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
        testHelper.assertTotalCount(16, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Error, 3, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.BEFORE_SEND, DataCategory.Error, 2, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.QUEUE_OVERFLOW, DataCategory.Transaction, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Span, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Transaction, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Error, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.UserReport, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Session, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Attachment, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Profile, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Monitor, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.MetricBucket, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Replay, 1, clientReportAtEnd)
    }

    @Test
    fun `lost transaction records dropped spans`() {
        givenClientReportRecorder()
        val hub = mock<IHub>()
        whenever(hub.options).thenReturn(opts)
        val transaction = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), hub)
        transaction.startChild("lost span", "span1").finish()
        transaction.startChild("lost span", "span2").finish()
        transaction.startChild("lost span", "span3").finish()
        transaction.startChild("lost span", "span4").finish()

        val envelope = testHelper.newEnvelope(
            SentryEnvelopeItem.fromEvent(opts.serializer, SentryTransaction(transaction))
        )

        clientReportRecorder.recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope)

        val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
        testHelper.assertTotalCount(6, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Span, 5, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Transaction, 1, clientReportAtEnd)
    }

    @Test
    fun `lost event can be recorded`() {
        givenClientReportRecorder()

        clientReportRecorder.recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error)

        val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
        testHelper.assertTotalCount(1, clientReport)
        testHelper.assertCountFor(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1, clientReport)
    }

    @Test
    fun `lost envelope item can be recorded`() {
        givenClientReportRecorder()

        val lostClientReport = ClientReport(
            DateUtils.getCurrentDateTime(),
            listOf(
                DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 3),
                DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 2),
                DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Profile.category, 2)
            )
        )

        val envelopeItem = SentryEnvelopeItem.fromClientReport(opts.serializer, lostClientReport)

        clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, envelopeItem)

        val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
        testHelper.assertTotalCount(8, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Error, 3, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.BEFORE_SEND, DataCategory.Error, 2, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.QUEUE_OVERFLOW, DataCategory.Transaction, 1, clientReportAtEnd)
        testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Profile, 2, clientReportAtEnd)
    }

    @Test
    fun `attaching client report to an envelope resets counts`() {
        givenClientReportRecorder()

        clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
        clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
        clientReportRecorder.recordLostEvent(DiscardReason.RATELIMIT_BACKOFF, DataCategory.Error)
        clientReportRecorder.recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.Error)
        clientReportRecorder.recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Profile)

        val envelope = clientReportRecorder.attachReportToEnvelope(testHelper.newEnvelope())

        testHelper.assertTotalCount(0, clientReportRecorder.resetCountsAndGenerateClientReport())

        val envelopeReport = envelope.items.first().getClientReport(opts.serializer)!!
        assertEquals(4, envelopeReport.discardedEvents.size)
        assertEquals(2, envelopeReport.discardedEvents.first { it.reason == DiscardReason.CACHE_OVERFLOW.reason && it.category == DataCategory.Attachment.category }.quantity)
        assertEquals(1, envelopeReport.discardedEvents.first { it.reason == DiscardReason.RATELIMIT_BACKOFF.reason && it.category == DataCategory.Error.category }.quantity)
        assertEquals(1, envelopeReport.discardedEvents.first { it.reason == DiscardReason.QUEUE_OVERFLOW.reason && it.category == DataCategory.Error.category }.quantity)
        assertEquals(1, envelopeReport.discardedEvents.first { it.reason == DiscardReason.BEFORE_SEND.reason && it.category == DataCategory.Profile.category }.quantity)
        assertTrue(
            ChronoUnit.MILLIS.between(
                LocalDateTime.now(),
                envelopeReport.timestamp.toInstant().atZone(
                    ZoneId.systemDefault()
                ).toLocalDateTime()
            ) < 10000
        )
    }

    private fun givenClientReportRecorder(callback: Sentry.OptionsConfiguration<SentryOptions>? = null) {
        setupSentry { options ->
            callback?.configure(options)
        }
        clientReportRecorder = opts.clientReportRecorder as ClientReportRecorder
        testHelper = ClientReportTestHelper(opts)
    }

    private fun setupSentry(callback: Sentry.OptionsConfiguration<SentryOptions>? = null) {
        Sentry.init { options ->
            options.dsn = dsnString
            callback?.configure(options)
            opts = options
        }
    }
}

class DropEverythingEventProcessor : EventProcessor {

    override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
        return null
    }

    override fun process(
        transaction: SentryTransaction,
        hint: Hint
    ): SentryTransaction? {
        return null
    }
}

class ClientReportTestHelper(val options: SentryOptions) {

    val reasons = DiscardReason.values()
    val categories = DataCategory.values()

    fun assertTotalCount(expectedCount: Long, clientReport: ClientReport?) {
        assertEquals(expectedCount, clientReport?.discardedEvents?.sumOf { it.quantity } ?: 0L)
    }

    fun assertCountFor(reason: DiscardReason, category: DataCategory, expectedCount: Long, clientReport: ClientReport?) {
        val discardedEvent = clientReport?.discardedEvents?.first { it.category == category.category && it.reason == reason.reason }
        assertEquals(expectedCount, discardedEvent?.quantity ?: 0L)
    }

    fun randomCategory(): DataCategory {
        return categories.random()
    }

    fun randomReason(): DiscardReason {
        return reasons.random()
    }

    fun newEnvelope(vararg items: SentryEnvelopeItem): SentryEnvelope {
        val header = SentryEnvelopeHeader(SentryId(UUID.randomUUID()))
        return SentryEnvelope(header, items.toList())
    }

    fun toEnvelopeItem(clientReport: ClientReport): SentryEnvelopeItem {
        return SentryEnvelopeItem.fromClientReport(options.serializer, clientReport)
    }

    companion object {
        fun retryableHint() = HintUtils.createWithTypeCheckHint(TestRetryable())
        fun uncaughtExceptionHint() = HintUtils.createWithTypeCheckHint(TestUncaughtExceptionHint())
        fun retryableUncaughtExceptionHint() = HintUtils.createWithTypeCheckHint(TestRetryableUncaughtException())

        fun assertClientReport(clientReportRecorder: IClientReportRecorder, expectedEvents: List<DiscardedEvent>) {
            val recorder = clientReportRecorder as ClientReportRecorder
            val clientReport = recorder.resetCountsAndGenerateClientReport()
            assertClientReport(clientReport, expectedEvents)
        }

        fun assertClientReport(clientReport: ClientReport?, expectedEvents: List<DiscardedEvent>) {
            assertEquals(expectedEvents.filter { it.quantity > 0 }.size, clientReport?.discardedEvents?.size ?: 0)

            expectedEvents.forEach { expectedEvent ->
                val actualEvent =
                    clientReport?.discardedEvents?.firstOrNull { it.reason == expectedEvent.reason && it.category == expectedEvent.category }
                assertEquals(expectedEvent.quantity, actualEvent?.quantity ?: 0, clientReport?.discardedEvents?.toString())
            }
        }
    }
}

class TestRetryable : Retryable {
    private var retry = false

    override fun setRetry(retry: Boolean) {
        this.retry = retry
    }

    override fun isRetry(): Boolean {
        return this.retry
    }
}

class TestRetryableUncaughtException : UncaughtExceptionHint(0, NoOpLogger.getInstance()), Retryable {
    private var retry = false
    var flushed = false

    override fun setRetry(retry: Boolean) {
        this.retry = retry
    }

    override fun isRetry(): Boolean {
        return this.retry
    }

    override fun markFlushed() {
        flushed = true
    }
}

class TestUncaughtExceptionHint : UncaughtExceptionHint(0, NoOpLogger.getInstance()) {
    var flushed = false

    override fun markFlushed() {
        flushed = true
    }
}

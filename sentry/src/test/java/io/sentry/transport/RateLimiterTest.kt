package io.sentry.transport

import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.ISerializer
import io.sentry.NoOpLogger
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryOptionsManipulator
import io.sentry.SentryTracer
import io.sentry.Session
import io.sentry.TransactionContext
import io.sentry.UserFeedback
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.IClientReportRecorder
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RateLimiterTest {

    private class Fixture {
        val currentDateProvider = mock<ICurrentDateProvider>()
        val clientReportRecorder = mock<IClientReportRecorder>()
        val serializer = mock<ISerializer>()

        fun getSUT(): RateLimiter {
            val options = SentryOptions().apply {
                setLogger(NoOpLogger.getInstance())
            }

            SentryOptionsManipulator.setClientReportRecorder(options, clientReportRecorder)

            return RateLimiter(
                currentDateProvider,
                options
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `uses X-Sentry-Rate-Limit and allows sending if time has passed`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("50:transaction:key, 1:default;error;security:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNotNull(result)
        assertEquals(1, result.items.count())
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be true`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)
        val hub: IHub = mock()
        whenever(hub.options).thenReturn(SentryOptions())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val transaction = SentryTransaction(SentryTracer(TransactionContext("name", "op"), hub))
        val transactionItem = SentryEnvelopeItem.fromEvent(fixture.serializer, transaction)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, transactionItem))

        rateLimiter.updateRetryAfterLimits("50:transaction:key, 2700:default;error;security:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be false`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val hub: IHub = mock()
        whenever(hub.options).thenReturn(SentryOptions())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val transaction = SentryTransaction(SentryTracer(TransactionContext("name", "op"), hub))
        val transactionItem = SentryEnvelopeItem.fromEvent(fixture.serializer, transaction)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, transactionItem))

        rateLimiter.updateRetryAfterLimits("1:transaction:key, 1:default;error;security:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNotNull(result)
        assertEquals(2, result.items.count())
    }

    @Test
    fun `When X-Sentry-Rate-Limit categories are empty, applies to all the categories`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("50::key", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `When all categories is set but expired, applies only for specific category`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("1::key, 60:default;error;security:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `When category has shorter rate limiting, do not apply new timestamp`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("60:error:key, 1:error:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `When category has longer rate limiting, apply new timestamp`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("1:error:key, 5:error:organization", null, 1)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `When both retry headers are not present, default delay is set`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits(null, null, 429)

        val result = rateLimiter.filter(envelope, Hint())
        assertNull(result)
    }

    @Test
    fun `records dropped items as lost`() {
        val rateLimiter = fixture.getSUT()

        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val userFeedbackItem = SentryEnvelopeItem.fromUserFeedback(
            fixture.serializer,
            UserFeedback(
                SentryId(UUID.randomUUID())
            ).also {
                it.comments = "It broke on Android. I don't know why, but this happens."
                it.email = "john@me.com"
                it.setName("John Me")
            }
        )
        val sessionItem = SentryEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
        val attachmentItem = SentryEnvelopeItem.fromAttachment(Attachment("{ \"number\": 10 }".toByteArray(), "log.json"), 1000)

        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, userFeedbackItem, sessionItem, attachmentItem))

        rateLimiter.updateRetryAfterLimits(null, null, 429)
        val result = rateLimiter.filter(envelope, Hint())

        assertNull(result)

        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(sessionItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(userFeedbackItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(attachmentItem))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records only dropped items as lost`() {
        val rateLimiter = fixture.getSUT()

        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val userFeedbackItem = SentryEnvelopeItem.fromUserFeedback(
            fixture.serializer,
            UserFeedback(
                SentryId(UUID.randomUUID())
            ).also {
                it.comments = "It broke on Android. I don't know why, but this happens."
                it.email = "john@me.com"
                it.setName("John Me")
            }
        )
        val sessionItem = SentryEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
        val attachmentItem = SentryEnvelopeItem.fromAttachment(Attachment("{ \"number\": 10 }".toByteArray(), "log.json"), 1000)

        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, userFeedbackItem, sessionItem, attachmentItem))

        rateLimiter.updateRetryAfterLimits("60:error:key, 1:error:organization", null, 1)
        val result = rateLimiter.filter(envelope, Hint())

        assertNotNull(result)
        assertEquals(3, result.items.toList().size)

        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }
}

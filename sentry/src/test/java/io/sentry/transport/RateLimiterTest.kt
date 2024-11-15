package io.sentry.transport

import io.sentry.Attachment
import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISerializer
import io.sentry.NoOpLogger
import io.sentry.ProfilingTraceData
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
import io.sentry.hints.DiskFlushNotification
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val scopes: IScopes = mock()
        whenever(scopes.options).thenReturn(SentryOptions())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val transaction = SentryTransaction(SentryTracer(TransactionContext("name", "op"), scopes))
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
        val scopes: IScopes = mock()
        whenever(scopes.options).thenReturn(SentryOptions())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val transaction = SentryTransaction(SentryTracer(TransactionContext("name", "op"), scopes))
        val transactionItem = SentryEnvelopeItem.fromEvent(fixture.serializer, transaction)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, transactionItem))

        rateLimiter.updateRetryAfterLimits("1:transaction:key, 1:default;error;metric_bucket;security:organization", null, 1)

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
        val scopes = mock<IScopes>()
        whenever(scopes.options).thenReturn(SentryOptions())
        val transaction = SentryTracer(TransactionContext("name", "op"), scopes)

        val sessionItem = SentryEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
        val attachmentItem = SentryEnvelopeItem.fromAttachment(fixture.serializer, NoOpLogger.getInstance(), Attachment("{ \"number\": 10 }".toByteArray(), "log.json"), 1000)
        val profileItem = SentryEnvelopeItem.fromProfilingTrace(ProfilingTraceData(File(""), transaction), 1000, fixture.serializer)
        val checkInItem = SentryEnvelopeItem.fromCheckIn(fixture.serializer, CheckIn("monitor-slug-1", CheckInStatus.ERROR))

        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, userFeedbackItem, sessionItem, attachmentItem, profileItem, checkInItem))

        rateLimiter.updateRetryAfterLimits(null, null, 429)
        val result = rateLimiter.filter(envelope, Hint())

        assertNull(result)

        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(sessionItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(userFeedbackItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(attachmentItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileItem))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(checkInItem))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records only dropped items as lost`() {
        val rateLimiter = fixture.getSUT()
        val scopes = mock<IScopes>()
        whenever(scopes.options).thenReturn(SentryOptions())

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
        val transaction = SentryTracer(TransactionContext("name", "op"), scopes)
        val profileItem = SentryEnvelopeItem.fromProfilingTrace(ProfilingTraceData(File(""), transaction), 1000, fixture.serializer)
        val sessionItem = SentryEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
        val attachmentItem = SentryEnvelopeItem.fromAttachment(fixture.serializer, NoOpLogger.getInstance(), Attachment("{ \"number\": 10 }".toByteArray(), "log.json"), 1000)

        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, userFeedbackItem, sessionItem, attachmentItem, profileItem))

        rateLimiter.updateRetryAfterLimits("60:error:key, 1:error:organization", null, 1)
        val result = rateLimiter.filter(envelope, Hint())

        assertNotNull(result)
        assertEquals(4, result.items.toList().size)

        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `drop profile items as lost`() {
        val rateLimiter = fixture.getSUT()
        val scopes = mock<IScopes>()
        whenever(scopes.options).thenReturn(SentryOptions())

        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val f = File.createTempFile("test", "trace")
        val transaction = SentryTracer(TransactionContext("name", "op"), scopes)
        val profileItem = SentryEnvelopeItem.fromProfilingTrace(ProfilingTraceData(f, transaction), 1000, fixture.serializer)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem, profileItem))

        rateLimiter.updateRetryAfterLimits("60:profile:key, 1:profile:organization", null, 1)
        val result = rateLimiter.filter(envelope, Hint())

        assertNotNull(result)
        assertEquals(1, result.items.toList().size)

        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileItem))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `any limit can be checked`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))

        assertFalse(rateLimiter.isAnyRateLimitActive)

        rateLimiter.updateRetryAfterLimits("50:transaction:key, 1:default;error;security:organization", null, 1)

        assertTrue(rateLimiter.isAnyRateLimitActive)
    }

    @Test
    fun `on rate limit DiskFlushNotification is marked as flushed`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)
        val sentryEvent = SentryEvent()
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, sentryEvent)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(sentryEvent.eventId), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("50:transaction:key, 1:default;error;security:organization", null, 1)

        val hint = mock<DiskFlushNotification>()
        whenever(hint.isFlushable(any())).thenReturn(true)

        rateLimiter.filter(envelope, HintUtils.createWithTypeCheckHint(hint))

        verify(hint).isFlushable(sentryEvent.eventId)
        verify(hint).markFlushed()
    }

    @Test
    fun `on rate limit DiskFlushNotification is not marked as flushed if not flushable`() {
        val rateLimiter = fixture.getSUT()
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)
        val sentryEvent = SentryEvent()
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.serializer, sentryEvent)
        val envelope = SentryEnvelope(SentryEnvelopeHeader(sentryEvent.eventId), arrayListOf(eventItem))

        rateLimiter.updateRetryAfterLimits("50:transaction:key, 1:default;error;security:organization", null, 1)

        val hint = mock<DiskFlushNotification>()
        whenever(hint.isFlushable(any())).thenReturn(false)

        rateLimiter.filter(envelope, HintUtils.createWithTypeCheckHint(hint))

        verify(hint).isFlushable(sentryEvent.eventId)
        verify(hint, never()).markFlushed()
    }
}

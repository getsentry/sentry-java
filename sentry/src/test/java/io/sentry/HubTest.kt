package io.sentry

import io.sentry.cache.EnvelopeCache
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.hints.SessionEndHint
import io.sentry.hints.SessionStartHint
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.callMethod
import io.sentry.util.HintUtils
import io.sentry.util.StringUtils
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.Queue
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class HubTest {

    private lateinit var file: File
    private lateinit var profilingTraceFile: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
        profilingTraceFile = Files.createTempFile("trace", ".trace").toFile()
        profilingTraceFile.writeText("sampledProfile")
    }

    @AfterTest
    fun shutdown() {
        file.deleteRecursively()
        profilingTraceFile.delete()
        Sentry.close()
    }

    @Test
    fun `when no dsn available, ctor throws illegal arg`() {
        val ex = assertFailsWith<IllegalArgumentException> { Hub(SentryOptions()) }
        assertEquals("Hub requires a DSN to be instantiated. Considering using the NoOpHub if no DSN is available.", ex.message)
    }

    @Test
    fun `when hub is cloned, integrations are not registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.addIntegration(integrationMock)
//        val expected = HubAdapter.getInstance()
        val hub = Hub(options)
//        verify(integrationMock).register(expected, options)
        hub.clone()
        verifyNoMoreInteractions(integrationMock)
    }

    @Test
    fun `when hub is cloned, scope changes are isolated`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val hub = Hub(options)
        var firstScope: Scope? = null
        hub.configureScope {
            firstScope = it
            it.setTag("hub", "a")
        }
        var cloneScope: Scope? = null
        val clone = hub.clone()
        clone.configureScope {
            cloneScope = it
            it.setTag("hub", "b")
        }
        assertEquals("a", firstScope!!.tags["hub"])
        assertEquals("b", cloneScope!!.tags["hub"])
    }

    @Test
    fun `when hub is initialized, breadcrumbs are capped as per options`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.maxBreadcrumbs = 5
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        (1..10).forEach { _ -> sut.addBreadcrumb(Breadcrumb(), null) }
        var actual = 0
        sut.configureScope {
            actual = it.breadcrumbs.size
        }
        assertEquals(options.maxBreadcrumbs, actual)
    }

    @Test
    fun `when beforeBreadcrumb returns null, crumb is dropped`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _: Breadcrumb, _: Any? -> null }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.addBreadcrumb(Breadcrumb(), null)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(0, breadcrumbs!!.size)
    }

    @Test
    fun `when beforeBreadcrumb modifies crumb, crumb is stored modified`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        val expected = "expected"
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb: Breadcrumb, _: Any? -> breadcrumb.message = expected; breadcrumb; }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val crumb = Breadcrumb()
        crumb.message = "original"
        sut.addBreadcrumb(crumb)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.first().message)
    }

    @Test
    fun `when beforeBreadcrumb is null, crumb is stored`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = null
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val expected = Breadcrumb()
        sut.addBreadcrumb(expected)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.single())
    }

    @Test
    fun `when beforeSend throws an exception, breadcrumb adds an entry to the data field with exception message`() {
        val exception = Exception("test")

        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _: Breadcrumb, _: Any? -> throw exception }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)

        val actual = Breadcrumb()
        sut.addBreadcrumb(actual)

        assertEquals("test", actual.data["sentry:message"])
    }

    @Test
    fun `when initialized, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when addBreadcrumb is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.close()
        sut.addBreadcrumb(Breadcrumb())
        assertTrue(breadcrumbs!!.isEmpty())
    }

    @Test
    fun `when addBreadcrumb is called with message and category, breadcrumb object has values`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.addBreadcrumb("message", "category")
        assertEquals("message", breadcrumbs!!.single().message)
        assertEquals("category", breadcrumbs!!.single().category)
    }

    @Test
    fun `when addBreadcrumb is called with message, breadcrumb object has value`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.addBreadcrumb("message", "category")
        assertEquals("message", breadcrumbs!!.single().message)
        assertEquals("category", breadcrumbs!!.single().category)
    }

    @Test
    fun `when flush is called on disabled client, no-op`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.flush(1000)
        verify(mockClient, never()).flush(1000)
    }

    @Test
    fun `when flush is called, client flush gets called`() {
        val (sut, mockClient) = getEnabledHub()

        sut.flush(1000)
        verify(mockClient).flush(1000)
    }

    //region captureEvent tests
    @Test
    fun `when captureEvent is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.callMethod("captureEvent", SentryEvent::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureEvent is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.captureEvent(SentryEvent())
        verify(mockClient, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when captureEvent is called with a valid argument, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledHub()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called on disabled hub, lastEventId does not get overwritten`() {
        val (sut, mockClient) = getEnabledHub()
        whenever(mockClient.captureEvent(any(), any(), anyOrNull())).thenReturn(SentryId(UUID.randomUUID()))
        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        val lastEventId = sut.lastEventId
        sut.close()
        sut.captureEvent(event, hints)
        assertEquals(lastEventId, sut.lastEventId)
    }

    @Test
    fun `when captureEvent is called and session tracking is disabled, it should not capture a session`() {
        val (sut, mockClient) = getEnabledHub()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when captureEvent is called but no session started, it should not capture a session`() {
        val (sut, mockClient) = getEnabledHub()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when captureEvent is called and event has exception which has been previously attached with span context, sets span context to the event`() {
        val (sut, mockClient) = getEnabledHub()
        val exception = RuntimeException()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(exception, span, "tx-name")

        val event = SentryEvent(exception)

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertEquals(span.spanContext, event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called and event has exception which root cause has been previously attached with span context, sets span context to the event`() {
        val (sut, mockClient) = getEnabledHub()
        val rootCause = RuntimeException()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(rootCause, span, "tx-name")

        val event = SentryEvent(RuntimeException(rootCause))

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertEquals(span.spanContext, event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called and event has exception which non-root cause has been previously attached with span context, sets span context to the event`() {
        val (sut, mockClient) = getEnabledHub()
        val rootCause = RuntimeException()
        val exceptionAssignedToSpan = RuntimeException(rootCause)
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(exceptionAssignedToSpan, span, "tx-name")

        val event = SentryEvent(RuntimeException(exceptionAssignedToSpan))

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertEquals(span.spanContext, event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called and event has exception which has been previously attached with span context and trace context already set, does not set new span context to the event`() {
        val (sut, mockClient) = getEnabledHub()
        val exception = RuntimeException()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(exception, span, "tx-name")

        val event = SentryEvent(exception)
        val originalSpanContext = SpanContext("op")
        event.contexts.trace = originalSpanContext

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertEquals(originalSpanContext, event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called and event has exception which has not been previously attached with span context, does not set new span context to the event`() {
        val (sut, mockClient) = getEnabledHub()

        val event = SentryEvent(RuntimeException())

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertNull(event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledHub()

        sut.captureEvent(SentryEvent(), null) {
            it.setTag("test", "testValue")
        }

        verify(mockClient).captureEvent(
            any(),
            check {
                assertEquals("testValue", it.tags["test"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `when captureEvent is called with a ScopeCallback then subsequent calls to captureEvent send the unmodified Scope to the client`() {
        val (sut, mockClient) = getEnabledHub()
        val argumentCaptor = argumentCaptor<Scope>()

        sut.captureEvent(SentryEvent(), null) {
            it.setTag("test", "testValue")
        }

        sut.captureEvent(SentryEvent())

        verify(mockClient, times(2)).captureEvent(
            any(),
            argumentCaptor.capture(),
            anyOrNull()
        )

        assertEquals("testValue", argumentCaptor.allValues[0].tags["test"])
        assertNull(argumentCaptor.allValues[1].tags["test"])
    }

    @Test
    fun `when captureEvent is called with a ScopeCallback that crashes then the event should still be captured`() {
        val (sut, mockClient, logger) = getEnabledHub()

        val exception = Exception("scope callback exception")
        sut.captureEvent(SentryEvent(), null) {
            throw exception
        }

        verify(mockClient).captureEvent(
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    //region captureMessage tests
    @Test
    fun `when captureMessage is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.callMethod("captureMessage", String::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureMessage is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.captureMessage("test")
        verify(mockClient, never()).captureMessage(any(), any())
    }

    @Test
    fun `when captureMessage is called with a valid message, captureMessage on the client should be called`() {
        val (sut, mockClient) = getEnabledHub()

        sut.captureMessage("test")
        verify(mockClient).captureMessage(any(), any(), any())
    }

    @Test
    fun `when captureMessage is called, level is INFO by default`() {
        val (sut, mockClient) = getEnabledHub()
        sut.captureMessage("test")
        verify(mockClient).captureMessage(eq("test"), eq(SentryLevel.INFO), any())
    }

    @Test
    fun `when captureMessage is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledHub()

        sut.captureMessage("test") {
            it.setTag("test", "testValue")
        }

        verify(mockClient).captureMessage(
            any(),
            any(),
            check {
                assertEquals("testValue", it.tags["test"])
            }
        )
    }

    @Test
    fun `when captureMessage is called with a ScopeCallback then subsequent calls to captureMessage send the unmodified Scope to the client`() {
        val (sut, mockClient) = getEnabledHub()
        val argumentCaptor = argumentCaptor<Scope>()

        sut.captureMessage("testMessage") {
            it.setTag("test", "testValue")
        }

        sut.captureMessage("test", SentryLevel.INFO)

        verify(mockClient, times(2)).captureMessage(
            any(),
            any(),
            argumentCaptor.capture()
        )

        assertEquals("testValue", argumentCaptor.allValues[0].tags["test"])
        assertNull(argumentCaptor.allValues[1].tags["test"])
    }

    @Test
    fun `when captureMessage is called with a ScopeCallback that crashes then the message should still be captured`() {
        val (sut, mockClient, logger) = getEnabledHub()

        val exception = Exception("scope callback exception")
        sut.captureMessage("Hello World") {
            throw exception
        }

        verify(mockClient).captureMessage(
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }

    //endregion

    //region captureException tests
    @Test
    fun `when captureException is called and exception is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.callMethod("captureException", Throwable::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureException is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.captureException(Throwable())
        verify(mockClient, never()).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument and hint, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledHub()

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureException(Throwable(), hints)
        verify(mockClient).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument but no hint, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledHub()

        sut.captureException(Throwable())
        verify(mockClient).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with an exception which has been previously attached with span context, span context should be set on the event before capturing`() {
        val (sut, mockClient) = getEnabledHub()
        val throwable = Throwable()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(throwable, span, "tx-name")

        sut.captureException(throwable)
        verify(mockClient).captureEvent(
            check {
                assertEquals(span.spanContext, it.contexts.trace)
                assertEquals("tx-name", it.transaction)
            },
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `when captureException is called with an exception which has not been previously attached with span context, span context should not be set on the event before capturing`() {
        val (sut, mockClient) = getEnabledHub()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(Throwable(), span, "tx-name")

        sut.captureException(Throwable())
        verify(mockClient).captureEvent(
            check {
                assertNull(it.contexts.trace)
            },
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `when captureException is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledHub()

        sut.captureException(Throwable(), null) {
            it.setTag("test", "testValue")
        }

        verify(mockClient).captureEvent(
            any(),
            check {
                assertEquals("testValue", it.tags["test"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `when captureException is called with a ScopeCallback then subsequent calls to captureException send the unmodified Scope to the client`() {
        val (sut, mockClient) = getEnabledHub()
        val argumentCaptor = argumentCaptor<Scope>()

        sut.captureException(Throwable(), null) {
            it.setTag("test", "testValue")
        }

        sut.captureException(Throwable())

        verify(mockClient, times(2)).captureEvent(
            any(),
            argumentCaptor.capture(),
            anyOrNull()
        )

        assertEquals("testValue", argumentCaptor.allValues[0].tags["test"])
        assertNull(argumentCaptor.allValues[1].tags["test"])
    }

    @Test
    fun `when captureException is called with a ScopeCallback that crashes then the exception should still be captured`() {
        val (sut, mockClient, logger) = getEnabledHub()

        val exception = Exception("scope callback exception")
        sut.captureException(Throwable()) {
            throw exception
        }

        verify(mockClient).captureEvent(
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }

    //endregion

    //region captureUserFeedback tests

    @Test
    fun `when captureUserFeedback is called it is forwarded to the client`() {
        val (sut, mockClient) = getEnabledHub()
        sut.captureUserFeedback(userFeedback)

        verify(mockClient).captureUserFeedback(
            check {
                assertEquals(userFeedback.eventId, it.eventId)
                assertEquals(userFeedback.email, it.email)
                assertEquals(userFeedback.name, it.name)
                assertEquals(userFeedback.comments, it.comments)
            }
        )
    }

    @Test
    fun `when captureUserFeedback is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.captureUserFeedback(userFeedback)
        verify(mockClient, never()).captureUserFeedback(any())
    }

    @Test
    fun `when captureUserFeedback is called and client throws, don't crash`() {
        val (sut, mockClient) = getEnabledHub()

        whenever(mockClient.captureUserFeedback(any())).doThrow(IllegalArgumentException(""))

        sut.captureUserFeedback(userFeedback)
    }

    private val userFeedback: UserFeedback get() {
        val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
        return UserFeedback(eventId).apply {
            name = "John"
            email = "john@me.com"
            comments = "comment"
        }
    }

    //endregion

    //region close tests
    @Test
    fun `when close is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledHub()
        sut.close()

        sut.close()
        verify(mockClient).close() // 1 to close, but next one wont be recorded
    }

    @Test
    fun `when close is called and client is alive, close on the client should be called`() {
        val (sut, mockClient) = getEnabledHub()

        sut.close()
        verify(mockClient).close()
    }
    //endregion

    //region withScope tests
    @Test
    fun `when withScope is called on disabled client, do nothing`() {
        val (sut) = getEnabledHub()

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.withScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when withScope is called with alive client, run should be called`() {
        val (sut) = getEnabledHub()

        val scopeCallback = mock<ScopeCallback>()

        sut.withScope(scopeCallback)
        verify(scopeCallback).run(any())
    }

    @Test
    fun `when withScope throws an exception then it should be caught`() {
        val (hub, _, logger) = getEnabledHub()

        val exception = Exception("scope callback exception")
        val scopeCallback = ScopeCallback {
            throw exception
        }

        hub.withScope(scopeCallback)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    //region configureScope tests
    @Test
    fun `when configureScope is called on disabled client, do nothing`() {
        val (sut) = getEnabledHub()

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when configureScope is called with alive client, run should be called`() {
        val (sut) = getEnabledHub()

        val scopeCallback = mock<ScopeCallback>()

        sut.configureScope(scopeCallback)
        verify(scopeCallback).run(any())
    }

    @Test
    fun `when configureScope throws an exception then it should be caught`() {
        val (hub, _, logger) = getEnabledHub()

        val exception = Exception("scope callback exception")
        val scopeCallback = ScopeCallback {
            throw exception
        }

        hub.configureScope(scopeCallback)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    @Test
    fun `when integration is registered, hub is enabled`() {
        val mock = mock<Integration>()

        var options: SentryOptions? = null
        // init main hub and make it enabled
        Sentry.init {
            it.addIntegration(mock)
            it.dsn = "https://key@sentry.io/proj"
            it.cacheDirPath = file.absolutePath
            it.setSerializer(mock())
            options = it
        }

        doAnswer {
            val hub = it.arguments[0] as IHub
            assertTrue(hub.isEnabled)
        }.whenever(mock).register(any(), eq(options!!))

        verify(mock).register(any(), eq(options!!))
    }

    //region setLevel tests
    @Test
    fun `when setLevel is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setLevel(SentryLevel.INFO)
        assertNull(scope?.level)
    }

    @Test
    fun `when setLevel is called, level is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setLevel(SentryLevel.INFO)
        assertEquals(SentryLevel.INFO, scope?.level)
    }
    //endregion

    //region setTransaction tests
    @Test
    fun `when setTransaction is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setTransaction("test")
        assertNull(scope?.transactionName)
    }

    @Test
    fun `when setTransaction is called, and transaction is not set, transaction name is changed`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setTransaction("test")
        assertEquals("test", scope?.transactionName)
    }

    @Test
    fun `when setTransaction is called, and transaction is set, transaction name is changed`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        val tx = hub.startTransaction("test", "op")
        hub.configureScope { it.setTransaction(tx) }

        assertEquals("test", scope?.transactionName)
    }

    @Test
    fun `when startTransaction is called with different instrumenter, no-op is returned`() {
        val hub = generateHub()

        val transactionContext = TransactionContext("test", "op").also { it.instrumenter = Instrumenter.OTEL }
        val transactionOptions = TransactionOptions()
        val tx = hub.startTransaction(transactionContext, transactionOptions)

        assertTrue(tx is NoOpTransaction)
    }

    @Test
    fun `when startTransaction is called with different instrumenter, no-op is returned 2`() {
        val hub = generateHub() {
            it.instrumenter = Instrumenter.OTEL
        }

        val tx = hub.startTransaction("test", "op")

        assertTrue(tx is NoOpTransaction)
    }

    @Test
    fun `when startTransaction is called with configured instrumenter, it works`() {
        val hub = generateHub() {
            it.instrumenter = Instrumenter.OTEL
        }

        val transactionContext = TransactionContext("test", "op").also { it.instrumenter = Instrumenter.OTEL }
        val transactionOptions = TransactionOptions()
        val tx = hub.startTransaction(transactionContext, transactionOptions)

        assertFalse(tx is NoOpTransaction)
    }
    //endregion

    //region setUser tests
    @Test
    fun `when setUser is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setUser(User())
        assertNull(scope?.user)
    }

    @Test
    fun `when setUser is called, user is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        val user = User()
        hub.setUser(user)
        assertEquals(user, scope?.user)
    }
    //endregion

    //region setFingerprint tests
    @Test
    fun `when setFingerprint is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        val fingerprint = listOf("abc")
        hub.setFingerprint(fingerprint)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called with null parameter, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.callMethod("setFingerprint", List::class.java, null)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called, fingerprint is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        val fingerprint = listOf("abc")
        hub.setFingerprint(fingerprint)
        assertEquals(1, scope?.fingerprint?.count())
    }
    //endregion

    //region clearBreadcrumbs tests
    @Test
    fun `when clearBreadcrumbs is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())

        hub.close()

        hub.clearBreadcrumbs()
        assertEquals(1, scope?.breadcrumbs?.count())
    }

    @Test
    fun `when clearBreadcrumbs is called, clear breadcrumbs`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())
        hub.clearBreadcrumbs()
        assertEquals(0, scope?.breadcrumbs?.count())
    }
    //endregion

    //region setTag tests
    @Test
    fun `when setTag is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setTag("test", "test")
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called with null parameters, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.callMethod("setTag", parameterTypes = arrayOf(String::class.java, String::class.java), null, null)
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called, tag is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setTag("test", "test")
        assertEquals(1, scope?.tags?.count())
    }
    //endregion

    //region setExtra tests
    @Test
    fun `when setExtra is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setExtra("test", "test")
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called with null parameters, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.callMethod("setExtra", parameterTypes = arrayOf(String::class.java, String::class.java), null, null)
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called, extra is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setExtra("test", "test")
        assertEquals(1, scope?.extras?.count())
    }
    //endregion

    //region captureEnvelope tests
    @Test
    fun `when captureEnvelope is called and envelope is null, throws IllegalArgumentException`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        try {
            sut.callMethod("captureEnvelope", SentryEnvelope::class.java, null)
            fail()
        } catch (e: Exception) {
            assertTrue(e.cause is java.lang.IllegalArgumentException)
        }
    }

    @Test
    fun `when captureEnvelope is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureEnvelope(SentryEnvelope(SentryId(UUID.randomUUID()), null, setOf()))
        verify(mockClient, never()).captureEnvelope(any(), any())
    }

    @Test
    fun `when captureEnvelope is called with a valid envelope, captureEnvelope on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val envelope = SentryEnvelope(SentryId(UUID.randomUUID()), null, setOf())
        sut.captureEnvelope(envelope)
        verify(mockClient).captureEnvelope(any(), anyOrNull())
    }

    @Test
    fun `when captureEnvelope is called, lastEventId is not set`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            setSerializer(mock())
        }
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        whenever(mockClient.captureEnvelope(any(), anyOrNull())).thenReturn(SentryId())
        val envelope = SentryEnvelope(SentryId(UUID.randomUUID()), null, setOf())
        sut.captureEnvelope(envelope)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }
    //endregion

    //region startSession tests
    @Test
    fun `when startSession is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.startSession()
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when startSession is called, starts a session`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.startSession()
        verify(mockClient).captureSession(any(), argWhere { HintUtils.hasType(it, SessionStartHint::class.java) })
    }

    @Test
    fun `when startSession is called and there's a session, stops it and starts a new one`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.startSession()
        sut.startSession()
        verify(mockClient).captureSession(any(), argWhere { HintUtils.hasType(it, SessionEndHint::class.java) })
        verify(mockClient, times(2)).captureSession(any(), argWhere { HintUtils.hasType(it, SessionStartHint::class.java) })
    }
    //endregion

    //region endSession tests
    @Test
    fun `when endSession is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.endSession()
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when endSession is called and session tracking is disabled, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.endSession()
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when endSession is called, end a session`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.startSession()
        sut.endSession()
        verify(mockClient).captureSession(any(), argWhere { HintUtils.hasType(it, SessionStartHint::class.java) })
        verify(mockClient).captureSession(any(), argWhere { HintUtils.hasType(it, SessionEndHint::class.java) })
    }

    @Test
    fun `when endSession is called and there's no session, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.release = "0.0.1"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.endSession()
        verify(mockClient, never()).captureSession(any(), any())
    }
    //endregion

    //region captureTransaction tests
    @Test
    fun `when captureTransaction is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        val sentryTracer = SentryTracer(TransactionContext("name", "op"), sut)
        sentryTracer.finish()
        sut.captureTransaction(SentryTransaction(sentryTracer), null as TraceContext?)
        verify(mockClient, never()).captureTransaction(any(), any(), any())
        verify(mockClient, never()).captureTransaction(any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureTransaction and transaction is sampled, captureTransaction on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), sut)
        sentryTracer.finish()
        val traceContext = sentryTracer.traceContext()
        verify(mockClient).captureTransaction(any(), equalTraceContext(traceContext), any(), eq(null), eq(null))
    }

    @Test
    fun `when captureTransaction is called, lastEventId is not set`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            setSerializer(mock())
        }
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        whenever(mockClient.captureTransaction(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(SentryId())

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), sut)
        sentryTracer.finish()
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureTransaction and transaction is not finished, captureTransaction on the client should not be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), sut)
        sut.captureTransaction(SentryTransaction(sentryTracer), null as TraceContext?)
        verify(mockClient, never()).captureTransaction(any(), any(), any(), eq(null), anyOrNull())
    }

    @Test
    fun `when captureTransaction and transaction is not sampled, captureTransaction on the client should not be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(false)), sut)
        sentryTracer.finish()
        val traceContext = sentryTracer.traceContext()
        verify(mockClient, never()).captureTransaction(any(), equalTraceContext(traceContext), any(), eq(null), anyOrNull())
    }

    @Test
    fun `transactions lost due to sampling are recorded as lost`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(false)), sut)
        sentryTracer.finish()

        assertClientReport(
            options.clientReportRecorder,
            listOf(DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Transaction.category, 1))
        )
    }
    //endregion

    //region profiling tests

    @Test
    fun `when startTransaction and profiling is enabled, transaction is profiled only if sampled`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        val mockClient = mock<ISentryClient>()
        whenever(mockTransactionProfiler.onTransactionFinish(any(), anyOrNull())).thenAnswer { mockClient.captureEnvelope(mock()) }
        val hub = generateHub {
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        hub.bindClient(mockClient)
        // Transaction is not sampled, so it should not be profiled
        val contexts = TransactionContext("name", "op", TracesSamplingDecision(false, null, true, null))
        val transaction = hub.startTransaction(contexts)
        transaction.finish()
        verify(mockClient, never()).captureEnvelope(any())

        // Transaction is sampled, so it should be profiled
        val sampledContexts = TransactionContext("name", "op", TracesSamplingDecision(true, null, true, null))
        val sampledTransaction = hub.startTransaction(sampledContexts)
        sampledTransaction.finish()
        verify(mockClient).captureEnvelope(any())
    }

    @Test
    fun `when startTransaction and is sampled but profiling is disabled, transaction is not profiled`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        val mockClient = mock<ISentryClient>()
        whenever(mockTransactionProfiler.onTransactionFinish(any(), anyOrNull())).thenAnswer { mockClient.captureEnvelope(mock()) }
        val hub = generateHub {
            it.profilesSampleRate = 0.0
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        hub.bindClient(mockClient)
        val contexts = TransactionContext("name", "op")
        val transaction = hub.startTransaction(contexts)
        transaction.finish()
        verify(mockClient, never()).captureEnvelope(any())
    }
    //endregion

    //region startTransaction tests
    @Test
    fun `when startTransaction, creates transaction`() {
        val hub = generateHub()
        val contexts = TransactionContext("name", "op")

        val transaction = hub.startTransaction(contexts)
        assertTrue(transaction is SentryTracer)
        assertEquals(contexts, transaction.root.spanContext)
    }

    @Test
    fun `when startTransaction with bindToScope set to false, transaction is not attached to the scope`() {
        val hub = generateHub()

        hub.startTransaction("name", "op", false)

        hub.configureScope {
            assertNull(it.span)
        }
    }

    @Test
    fun `when startTransaction without bindToScope set, transaction is not attached to the scope`() {
        val hub = generateHub()

        hub.startTransaction("name", "op")

        hub.configureScope {
            assertNull(it.span)
        }
    }

    @Test
    fun `when startTransaction with bindToScope set to true, transaction is attached to the scope`() {
        val hub = generateHub()

        val transaction = hub.startTransaction("name", "op", true)

        hub.configureScope {
            assertEquals(transaction, it.span)
        }
    }

    @Test
    fun `when startTransaction and no tracing sampling is configured, event is not sampled`() {
        val hub = generateHub {
            it.tracesSampleRate = 0.0
        }

        val transaction = hub.startTransaction("name", "op")
        assertFalse(transaction.isSampled!!)
    }

    @Test
    fun `when startTransaction and no profile sampling is configured, profile is not sampled`() {
        val hub = generateHub {
            it.tracesSampleRate = 1.0
            it.profilesSampleRate = 0.0
        }

        val transaction = hub.startTransaction("name", "op")
        assertTrue(transaction.isSampled!!)
        assertFalse(transaction.isProfileSampled!!)
    }

    @Test
    fun `when startTransaction with parent sampled and no traces sampler provided, transaction inherits sampling decision`() {
        val hub = generateHub()
        val transactionContext = TransactionContext("name", "op")
        transactionContext.parentSampled = true
        val transaction = hub.startTransaction(transactionContext)
        assertNotNull(transaction)
        assertNotNull(transaction.isSampled)
        assertTrue(transaction.isSampled!!)
    }

    @Test
    fun `when startTransaction with parent profile sampled and no profile sampler provided, transaction inherits profile sampling decision`() {
        val hub = generateHub()
        val transactionContext = TransactionContext("name", "op")
        transactionContext.setParentSampled(true, true)
        val transaction = hub.startTransaction(transactionContext)
        assertTrue(transaction.isProfileSampled!!)
    }

    @Test
    fun `Hub should close the sentry executor processor on close call`() {
        val executor = mock<ISentryExecutorService>()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            cacheDirPath = file.absolutePath
            executorService = executor
        }
        val sut = Hub(options)
        sut.close()
        verify(executor).close(any())
    }

    @Test
    fun `when tracesSampleRate and tracesSampler are not set on SentryOptions, startTransaction returns NoOp`() {
        val hub = generateHub {
            it.tracesSampleRate = null
            it.tracesSampler = null
        }
        val transaction = hub.startTransaction(TransactionContext("name", "op", TracesSamplingDecision(true)))
        assertTrue(transaction is NoOpTransaction)
    }
    //endregion

    //region startTransaction tests
    @Test
    fun `when traceHeaders and no transaction is active, traceHeaders are null`() {
        val hub = generateHub()

        assertNull(hub.traceHeaders())
    }

    @Test
    fun `when traceHeaders and there is an active transaction, traceHeaders are not null`() {
        val hub = generateHub()
        val tx = hub.startTransaction("aTransaction", "op")
        hub.configureScope { it.setTransaction(tx) }

        assertNotNull(hub.traceHeaders())
    }
    //endregion

    //region getSpan tests
    @Test
    fun `when there is no active transaction, getSpan returns null`() {
        val hub = generateHub()
        assertNull(hub.getSpan())
    }

    @Test
    fun `when there is active transaction bound to the scope, getSpan returns active transaction`() {
        val hub = generateHub()
        val tx = hub.startTransaction("aTransaction", "op")
        hub.configureScope { it.setTransaction(tx) }
        assertEquals(tx, hub.getSpan())
    }

    @Test
    fun `when there is active span within a transaction bound to the scope, getSpan returns active span`() {
        val hub = generateHub()
        val tx = hub.startTransaction("aTransaction", "op")
        hub.configureScope { it.setTransaction(tx) }
        hub.configureScope { it.setTransaction(tx) }
        val span = tx.startChild("op")
        assertEquals(span, hub.span)
    }
    // endregion

    //region setSpanContext
    @Test
    fun `associates span context with throwable`() {
        val (hub, mockClient) = getEnabledHub()
        val transaction = hub.startTransaction("aTransaction", "op")
        val span = transaction.startChild("op")
        val exception = RuntimeException()

        hub.setSpanContext(exception, span, "tx-name")
        hub.captureEvent(SentryEvent(exception))

        verify(mockClient).captureEvent(
            check {
                assertEquals(span.spanContext, it.contexts.trace)
            },
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `returns null when no span context associated with throwable`() {
        val hub = generateHub() as Hub
        assertNull(hub.getSpanContext(RuntimeException()))
    }
    // endregion

    @Test
    fun `isCrashedLastRun does not delete native marker if auto session is enabled`() {
        val nativeMarker = File(hashedFolder(), EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        nativeMarker.mkdirs()
        nativeMarker.createNewFile()
        val hub = generateHub() as Hub

        assertTrue(hub.isCrashedLastRun!!)
        assertTrue(nativeMarker.exists())
    }

    @Test
    fun `isCrashedLastRun deletes the native marker if auto session is disabled`() {
        val nativeMarker = File(hashedFolder(), EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        nativeMarker.mkdirs()
        nativeMarker.createNewFile()
        val hub = generateHub {
            it.isEnableAutoSessionTracking = false
        }

        assertTrue(hub.isCrashedLastRun!!)
        assertFalse(nativeMarker.exists())
    }

    @Test
    fun `reportFullyDrawn is ignored if TimeToFullDisplayTracing is disabled`() {
        var called = false
        val hub = generateHub {
            it.fullyDrawnReporter.registerFullyDrawnListener {
                called = !called
                true
            }
        }
        hub.reportFullDisplayed()
        assertFalse(called)
    }

    @Test
    fun `reportFullyDrawn calls FullyDrawnReporter if TimeToFullDisplayTracing is enabled`() {
        var called = false
        val hub = generateHub {
            it.isEnableTimeToFullDisplayTracing = true
            it.fullyDrawnReporter.registerFullyDrawnListener {
                called = !called
                true
            }
        }
        hub.reportFullDisplayed()
        assertTrue(called)
    }

    @Test
    fun `reportFullyDrawn calls FullyDrawnReporter only once`() {
        var called = false
        val hub = generateHub {
            it.isEnableTimeToFullDisplayTracing = true
            it.fullyDrawnReporter.registerFullyDrawnListener {
                called = !called
                true
            }
        }
        hub.reportFullDisplayed()
        assertTrue(called)
        hub.reportFullDisplayed()
        assertTrue(called)
    }

    private val dsnTest = "https://key@sentry.io/proj"

    private fun generateHub(optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions>? = null): IHub {
        val options = SentryOptions().apply {
            dsn = dsnTest
            cacheDirPath = file.absolutePath
            setSerializer(mock())
            tracesSampleRate = 1.0
        }
        optionsConfiguration?.configure(options)
        return Hub(options)
    }

    private fun getEnabledHub(): Triple<Hub, ISentryClient, ILogger> {
        val logger = mock<ILogger>()

        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.tracesSampleRate = 1.0
        options.isDebug = true
        options.setLogger(logger)

        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        return Triple(sut, mockClient, logger)
    }

    private fun hashedFolder(): String {
        val hash = StringUtils.calculateStringHash(dsnTest, mock())
        val fileHashFolder = File(file.absolutePath, hash!!)
        return fileHashFolder.absolutePath
    }

    private fun equalTraceContext(expectedContext: TraceContext?): TraceContext? {
        expectedContext ?: return eq<TraceContext?>(null)

        return argWhere { actual ->
            expectedContext.traceId == actual.traceId &&
                expectedContext.transaction == actual.transaction &&
                expectedContext.environment == actual.environment &&
                expectedContext.release == actual.release &&
                expectedContext.publicKey == actual.publicKey &&
                expectedContext.sampleRate == actual.sampleRate &&
                expectedContext.userId == actual.userId &&
                expectedContext.userSegment == actual.userSegment
        }
    }
}

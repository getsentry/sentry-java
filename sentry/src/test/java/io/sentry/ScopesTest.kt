package io.sentry

import io.sentry.backpressure.IBackpressureMonitor
import io.sentry.cache.EnvelopeCache
import io.sentry.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.DiscardedEvent
import io.sentry.hints.SessionEndHint
import io.sentry.hints.SessionStartHint
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import io.sentry.test.DeferredExecutorService
import io.sentry.test.callMethod
import io.sentry.test.createSentryClientMock
import io.sentry.test.createTestScopes
import io.sentry.test.initForTest
import io.sentry.util.HintUtils
import io.sentry.util.StringUtils
import junit.framework.TestCase.assertSame
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
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.Queue
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ScopesTest {

    private lateinit var file: File
    private lateinit var profilingTraceFile: File
    private val mockProfiler = spy(NoOpContinuousProfiler.getInstance())

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
        profilingTraceFile = Files.createTempFile("trace", ".trace").toFile()
        profilingTraceFile.writeText("sampledProfile")
        SentryCrashLastRunState.getInstance().reset()
    }

    @AfterTest
    fun shutdown() {
        file.deleteRecursively()
        profilingTraceFile.delete()
        Sentry.close()
        SentryCrashLastRunState.getInstance().reset()
    }

    private fun createScopes(options: SentryOptions): Scopes {
        return createTestScopes(options).also {
            it.bindClient(SentryClient(options))
        }
    }

    @Test
    fun `when no dsn available, ctor throws illegal arg`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            val options = SentryOptions()
            val scopeToUse = Scope(options)
            val isolationScopeToUse = Scope(options)
            val globalScopeToUse = Scope(options)
            Scopes(scopeToUse, isolationScopeToUse, globalScopeToUse, "test")
        }
        assertEquals("Scopes requires a DSN to be instantiated. Considering using the NoOpScopes if no DSN is available.", ex.message)
    }

    @Test
    fun `when isolation scope is forked, integrations are not registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.addIntegration(integrationMock)
        val scopes = createScopes(options)
        reset(integrationMock)
        scopes.forkedScopes("test")
        verifyNoMoreInteractions(integrationMock)
    }

    @Test
    fun `when current scope is forked, integrations are not registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.addIntegration(integrationMock)
        val scopes = createScopes(options)
        reset(integrationMock)
        scopes.forkedCurrentScope("test")
        verifyNoMoreInteractions(integrationMock)
    }

    @Test
    fun `when isolation scope is forked, scope changes are isolated`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val scopes = createScopes(options)
        var firstScope: IScope? = null
        scopes.configureScope {
            firstScope = it
            it.setTag("scopes", "a")
        }
        var cloneScope: IScope? = null
        val clone = scopes.forkedScopes("test")
        clone.configureScope {
            cloneScope = it
            it.setTag("scopes", "b")
        }
        assertEquals("a", firstScope!!.tags["scopes"])
        assertEquals("b", cloneScope!!.tags["scopes"])
    }

    @Test
    fun `when current scope is forked, scope changes are not isolated`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val scopes = createScopes(options)
        var firstScope: IScope? = null
        scopes.configureScope {
            firstScope = it
            it.setTag("scopes", "a")
        }
        var cloneScope: IScope? = null
        val clone = scopes.forkedCurrentScope("test")
        clone.configureScope {
            cloneScope = it
            it.setTag("scopes", "b")
        }
        assertEquals("b", firstScope!!.tags["scopes"])
        assertEquals("b", cloneScope!!.tags["scopes"])
    }

    @Test
    fun `when scopes is initialized, breadcrumbs are capped as per options`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.maxBreadcrumbs = 5
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
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
        val sut = createScopes(options)
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
        val sut = createScopes(options)
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
        val sut = createScopes(options)
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
        val sut = createScopes(options)

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
        val sut = createScopes(options)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when addBreadcrumb is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope(ScopeType.COMBINED) { breadcrumbs = it.breadcrumbs }
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
        val sut = createScopes(options)
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
        val sut = createScopes(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.addBreadcrumb("message", "category")
        assertEquals("message", breadcrumbs!!.single().message)
        assertEquals("category", breadcrumbs!!.single().category)
    }

    @Test
    fun `when flush is called on disabled client, no-op`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.flush(1000)
        verify(mockClient, never()).flush(1000)
    }

    @Test
    fun `when flush is called, client flush gets called`() {
        val (sut, mockClient) = getEnabledScopes()

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
        val sut = createScopes(options)
        sut.callMethod("captureEvent", SentryEvent::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureEvent is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureEvent(SentryEvent())
        verify(mockClient, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when captureEvent is called with a valid argument, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called on disabled scopes, lastEventId does not get overwritten`() {
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when captureEvent is called but no session started, it should not capture a session`() {
        val (sut, mockClient) = getEnabledScopes()

        val event = SentryEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
        verify(mockClient, never()).captureSession(any(), any())
    }

    @Test
    fun `when captureEvent is called and event has exception which has been previously attached with span context, sets span context to the event`() {
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()
        val exception = RuntimeException()
        val span = mock<Span>()
        whenever(span.spanContext).thenReturn(SpanContext("op"))
        sut.setSpanContext(exception, span, "tx-name")

        val event = SentryEvent(exception)
        val originalSpanContext = SpanContext("op")
        event.contexts.setTrace(originalSpanContext)

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertEquals(originalSpanContext, event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called and event has exception which has not been previously attached with span context, does not set new span context to the event`() {
        val (sut, mockClient) = getEnabledScopes()

        val event = SentryEvent(RuntimeException())

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureEvent(event, hints)
        assertNull(event.contexts.trace)
        verify(mockClient).captureEvent(eq(event), any(), eq(hints))
    }

    @Test
    fun `when captureEvent is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledScopes()

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
        val (sut, mockClient) = getEnabledScopes()
        val argumentCaptor = argumentCaptor<IScope>()

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
        val (sut, mockClient, logger) = getEnabledScopes()

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
        val sut = createScopes(options)
        sut.callMethod("captureMessage", String::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureMessage is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureMessage("test")
        verify(mockClient, never()).captureMessage(any(), any())
    }

    @Test
    fun `when captureMessage is called with a valid message, captureMessage on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.captureMessage("test")
        verify(mockClient).captureMessage(any(), any(), any())
    }

    @Test
    fun `when captureMessage is called, level is INFO by default`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.captureMessage("test")
        verify(mockClient).captureMessage(eq("test"), eq(SentryLevel.INFO), any())
    }

    @Test
    fun `when captureMessage is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledScopes()

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
        val (sut, mockClient) = getEnabledScopes()
        val argumentCaptor = argumentCaptor<IScope>()

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
        val (sut, mockClient, logger) = getEnabledScopes()

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

    //region captureFeedback tests
    @Test
    fun `when captureFeedback is called and message is empty, client is never called`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.captureFeedback(Feedback(""))
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
        verify(mockClient, never()).captureFeedback(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureFeedback is called, lastEventId is not updated`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.captureFeedback(Feedback("message"))
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
        verify(mockClient).captureFeedback(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureFeedback is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureFeedback(mock())
        verify(mockClient, never()).captureFeedback(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureFeedback is called with a valid message, captureFeedback on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.captureFeedback(Feedback("test"))
        verify(mockClient).captureFeedback(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when captureFeedback is called with a ScopeCallback then the modified scope is sent to the client`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.captureFeedback(Feedback("test"), null) {
            it.setTag("test", "testValue")
        }

        verify(mockClient).captureFeedback(
            any(),
            eq(null),
            check {
                assertEquals("testValue", it.tags["test"])
            }
        )
    }

    @Test
    fun `when captureFeedback is called with a ScopeCallback then subsequent calls to captureFeedback send the unmodified Scope to the client`() {
        val (sut, mockClient) = getEnabledScopes()
        val argumentCaptor = argumentCaptor<IScope>()

        sut.captureFeedback(Feedback("testMessage"), null) {
            it.setTag("test", "testValue")
        }

        sut.captureFeedback(Feedback("test"))

        verify(mockClient, times(2)).captureFeedback(
            any(),
            anyOrNull(),
            argumentCaptor.capture()
        )

        assertEquals("testValue", argumentCaptor.allValues[0].tags["test"])
        assertNull(argumentCaptor.allValues[1].tags["test"])
    }

    @Test
    fun `when captureFeedback is called with a ScopeCallback that crashes then the feedback should still be captured`() {
        val (sut, mockClient, logger) = getEnabledScopes()

        val exception = Exception("scope callback exception")
        sut.captureFeedback(Feedback("Hello World"), null) {
            throw exception
        }

        verify(mockClient).captureFeedback(
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }

    @Test
    fun `when captureFeedback is called with a Hint, it is passed to the client`() {
        val (sut, mockClient) = getEnabledScopes()
        val hint = Hint()

        sut.captureFeedback(Feedback("Hello World"), hint)

        verify(mockClient).captureFeedback(any(), eq(hint), anyOrNull())
    }

    //endregion

    //region captureException tests
    @Test
    fun `when captureException is called and exception is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
        sut.callMethod("captureException", Throwable::class.java, null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureException is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureException(Throwable())
        verify(mockClient, never()).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument and hint, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureException(Throwable(), hints)
        verify(mockClient).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument but no hint, captureEvent on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.captureException(Throwable())
        verify(mockClient).captureEvent(any(), any(), any())
    }

    @Test
    fun `when captureException is called with an exception which has been previously attached with span context, span context should be set on the event before capturing`() {
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()

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
        val (sut, mockClient) = getEnabledScopes()
        val argumentCaptor = argumentCaptor<IScope>()

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
        val (sut, mockClient, logger) = getEnabledScopes()

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
        val (sut, mockClient) = getEnabledScopes()
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
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureUserFeedback(userFeedback)
        verify(mockClient, never()).captureUserFeedback(any())
    }

    @Test
    fun `when captureUserFeedback is called and client throws, don't crash`() {
        val (sut, mockClient) = getEnabledScopes()

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

    //region captureCheckIn tests

    @Test
    fun `when captureCheckIn is called it is forwarded to the client`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.captureCheckIn(checkIn)

        verify(mockClient).captureCheckIn(
            check {
                assertEquals(checkIn.checkInId, it.checkInId)
                assertEquals(checkIn.monitorSlug, it.monitorSlug)
                assertEquals(checkIn.status, it.status)
            },
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `when captureCheckIn is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureCheckIn(checkIn)
        verify(mockClient, never()).captureCheckIn(any(), any(), anyOrNull())
    }

    @Test
    fun `when captureCheckIn is called and client throws, don't crash`() {
        val (sut, mockClient) = getEnabledScopes()

        whenever(mockClient.captureCheckIn(any(), any(), anyOrNull())).doThrow(IllegalArgumentException(""))

        sut.captureCheckIn(checkIn)
    }

    private val checkIn: CheckIn = CheckIn("some_slug", CheckInStatus.OK)

    //endregion

    //region close tests
    @Test
    fun `when close is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.close()
        verify(mockClient).close(eq(false)) // 1 to close, but next one wont be recorded
    }

    @Test
    fun `when close is called and client is alive, close on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.close()
        verify(mockClient).close(eq(false))
    }

    @Test
    fun `when close is called with isRestarting false and client is alive, close on the client should be called with isRestarting false`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.close(false)
        verify(mockClient).close(eq(false))
    }

    @Test
    fun `when close is called with isRestarting true and client is alive, close on the client should be called with isRestarting true`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.close(true)
        verify(mockClient).close(eq(true))
    }
    //endregion

    //region withScope tests
    @Test
    fun `when withScope is called on disabled client, execute on NoOpScope`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.withScope(scopeCallback)
        verify(scopeCallback).run(NoOpScope.getInstance())
    }

    @Test
    fun `when withScope is called with alive client, run should be called`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()

        sut.withScope(scopeCallback)
        verify(scopeCallback).run(any())
    }

    @Test
    fun `when withScope throws an exception then it should be caught`() {
        val (scopes, _, logger) = getEnabledScopes()

        val exception = Exception("scope callback exception")
        val scopeCallback = ScopeCallback {
            throw exception
        }

        scopes.withScope(scopeCallback)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    //region withIsolationScope tests
    @Test
    fun `when withIsolationScope is called on disabled client, execute on NoOpScope`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.withIsolationScope(scopeCallback)
        verify(scopeCallback).run(NoOpScope.getInstance())
    }

    @Test
    fun `when withIsolationScope is called with alive client, run should be called`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()

        sut.withIsolationScope(scopeCallback)
        verify(scopeCallback).run(any())
    }

    @Test
    fun `when withIsolationScope throws an exception then it should be caught`() {
        val (scopes, _, logger) = getEnabledScopes()

        val exception = Exception("scope callback exception")
        val scopeCallback = ScopeCallback {
            throw exception
        }

        scopes.withIsolationScope(scopeCallback)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    //region configureScope tests
    @Test
    fun `when configureScope is called on disabled client, do nothing`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when configureScope is called with alive client, run should be called`() {
        val (sut) = getEnabledScopes()

        val scopeCallback = mock<ScopeCallback>()

        sut.configureScope(scopeCallback)
        verify(scopeCallback).run(any())
    }

    @Test
    fun `when configureScope throws an exception then it should be caught`() {
        val (scopes, _, logger) = getEnabledScopes()

        val exception = Exception("scope callback exception")
        val scopeCallback = ScopeCallback {
            throw exception
        }

        scopes.configureScope(scopeCallback)

        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }
    //endregion

    @Test
    fun `when integration is registered, scopes is enabled`() {
        val mock = mock<Integration>()

        var options: SentryOptions? = null
        // init main scopes and make it enabled
        initForTest {
            it.addIntegration(mock)
            it.dsn = "https://key@sentry.io/proj"
            it.cacheDirPath = file.absolutePath
            it.setSerializer(mock())
            options = it
        }

        doAnswer {
            val scopes = it.arguments[0] as IScopes
            assertTrue(scopes.isEnabled)
        }.whenever(mock).register(any(), eq(options!!))

        verify(mock).register(any(), eq(options!!))
    }

    //region setLevel tests
    @Test
    fun `when setLevel is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        scopes.setLevel(SentryLevel.INFO)
        assertNull(scope?.level)
    }

    @Test
    fun `when setLevel is called, level is set`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.setLevel(SentryLevel.INFO)
        assertEquals(SentryLevel.INFO, scope?.level)
    }
    //endregion

    //region setTransaction tests
    @Test
    fun `when setTransaction is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        scopes.setTransaction("test")
        assertNull(scope?.transactionName)
    }

    @Test
    fun `when setTransaction is called, and transaction is not set, transaction name is changed`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.setTransaction("test")
        assertEquals("test", scope?.transactionName)
    }

    @Test
    fun `when setTransaction is called, and transaction is set, transaction name is changed`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        val tx = scopes.startTransaction("test", "op")
        scopes.configureScope { it.setTransaction(tx) }

        assertEquals("test", scope?.transactionName)
    }

    @Test
    fun `when startTransaction is called with different instrumenter, no-op is returned`() {
        val scopes = generateScopes()

        val transactionContext = TransactionContext("test", "op").also { it.instrumenter = Instrumenter.OTEL }
        val transactionOptions = TransactionOptions()
        val tx = scopes.startTransaction(transactionContext, transactionOptions)

        assertTrue(tx is NoOpTransaction)
    }

    @Test
    fun `when startTransaction is called with different instrumenter, no-op is returned 2`() {
        val scopes = generateScopes() {
            it.instrumenter = Instrumenter.OTEL
        }

        val tx = scopes.startTransaction("test", "op")

        assertTrue(tx is NoOpTransaction)
    }

    @Test
    fun `when startTransaction is called with configured instrumenter, it works`() {
        val scopes = generateScopes() {
            it.instrumenter = Instrumenter.OTEL
        }

        val transactionContext = TransactionContext("test", "op").also { it.instrumenter = Instrumenter.OTEL }
        val transactionOptions = TransactionOptions()
        val tx = scopes.startTransaction(transactionContext, transactionOptions)

        assertFalse(tx is NoOpTransaction)
    }
    //endregion

    //region setUser tests
    @Test
    fun `when setUser is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        scopes.setUser(User())
        assertNull(scope?.user)
    }

    @Test
    fun `when setUser is called, user is set`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        val user = User()
        scopes.setUser(user)
        assertEquals(user, scope?.user)
    }
    //endregion

    //region setFingerprint tests
    @Test
    fun `when setFingerprint is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        val fingerprint = listOf("abc")
        scopes.setFingerprint(fingerprint)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called with null parameter, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.callMethod("setFingerprint", List::class.java, null)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called, fingerprint is set`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        val fingerprint = listOf("abc")
        scopes.setFingerprint(fingerprint)
        assertEquals(1, scope?.fingerprint?.count())
    }
    //endregion

    //region clearBreadcrumbs tests
    @Test
    fun `when clearBreadcrumbs is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())

        scopes.close()

        assertEquals(0, scope?.breadcrumbs?.count())
    }

    @Test
    fun `when clearBreadcrumbs is called, clear breadcrumbs`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())
        scopes.clearBreadcrumbs()
        assertEquals(0, scope?.breadcrumbs?.count())
    }
    //endregion

    //region setTag tests
    @Test
    fun `when setTag is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        scopes.setTag("test", "test")
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called with null parameters, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.callMethod("setTag", parameterTypes = arrayOf(String::class.java, String::class.java), null, null)
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called, tag is set`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.setTag("test", "test")
        assertEquals(1, scope?.tags?.count())
    }
    //endregion

    //region setExtra tests
    @Test
    fun `when setExtra is called on disabled client, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }
        scopes.close()

        scopes.setExtra("test", "test")
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called with null parameters, do nothing`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.callMethod("setExtra", parameterTypes = arrayOf(String::class.java, String::class.java), null, null)
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called, extra is set`() {
        val scopes = generateScopes()
        var scope: IScope? = null
        scopes.configureScope {
            scope = it
        }

        scopes.setExtra("test", "test")
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
        val sut = createScopes(options)
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock(enabled = false)
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
        sut.bindClient(mockClient)
        sut.close()

        sut.configureScope(ScopeType.ISOLATION) { scope ->
            scope.client.isEnabled
        }

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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock(enabled = false)
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
        sut.bindClient(mockClient)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(false)), sut)
        // Unsampled spans are not added to the transaction, so they are not recorded
        sentryTracer.startChild("dropped span", "span 1").finish()
        sentryTracer.finish()

        assertClientReport(
            options.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Span.category, 1)
            )
        )
    }

    @Test
    fun `transactions lost due to sampling caused by backpressure are recorded as lost`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
        sut.bindClient(mockClient)
        val mockBackpressureMonitor = mock<IBackpressureMonitor>()
        options.backpressureMonitor = mockBackpressureMonitor
        whenever(mockBackpressureMonitor.downsampleFactor).thenReturn(1)

        val sentryTracer = SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(false)), sut)
        // Unsampled spans are not added to the transaction, so they are not recorded
        sentryTracer.startChild("dropped span", "span 1").finish()
        sentryTracer.finish()

        assertClientReport(
            options.clientReportRecorder,
            listOf(
                DiscardedEvent(DiscardReason.BACKPRESSURE.reason, DataCategory.Transaction.category, 1),
                DiscardedEvent(DiscardReason.BACKPRESSURE.reason, DataCategory.Span.category, 1)
            )
        )
    }
    //endregion

    //region captureProfileChunk tests
    @Test
    fun `when captureProfileChunk is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureProfileChunk(mock())
        verify(mockClient, never()).captureProfileChunk(any(), any())
        verify(mockClient, never()).captureProfileChunk(any(), any())
    }

    @Test
    fun `when captureProfileChunk, captureProfileChunk on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
        sut.bindClient(mockClient)

        val profileChunk = mock<ProfileChunk>()
        sut.captureProfileChunk(profileChunk)
        verify(mockClient).captureProfileChunk(eq(profileChunk), any())
    }

    @Test
    fun `when profileChunk is called, lastEventId is not set`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            setSerializer(mock())
        }
        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
        sut.bindClient(mockClient)
        sut.captureProfileChunk(mock())
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }
    //endregion

    //region profiling tests

    @Test
    fun `when startTransaction and profiling is enabled, transaction is profiled only if sampled`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        val mockClient = createSentryClientMock()
        whenever(mockTransactionProfiler.onTransactionFinish(any(), anyOrNull(), anyOrNull())).thenAnswer { mockClient.captureEnvelope(mock()) }
        val scopes = generateScopes {
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        scopes.bindClient(mockClient)
        // Transaction is not sampled, so it should not be profiled
        val contexts = TransactionContext("name", "op", TracesSamplingDecision(false, null, true, null))
        val transaction = scopes.startTransaction(contexts)
        transaction.finish()
        verify(mockClient, never()).captureEnvelope(any())

        // Transaction is sampled, so it should be profiled
        val sampledContexts = TransactionContext("name", "op", TracesSamplingDecision(true, null, true, null))
        val sampledTransaction = scopes.startTransaction(sampledContexts)
        sampledTransaction.finish()
        verify(mockClient).captureEnvelope(any())
    }

    @Test
    fun `when startTransaction and is sampled but profiling is disabled, transaction is not profiled`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        val mockClient = createSentryClientMock()
        whenever(mockTransactionProfiler.onTransactionFinish(any(), anyOrNull(), anyOrNull())).thenAnswer { mockClient.captureEnvelope(mock()) }
        val scopes = generateScopes {
            it.profilesSampleRate = 0.0
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        scopes.bindClient(mockClient)
        val contexts = TransactionContext("name", "op")
        val transaction = scopes.startTransaction(contexts)
        transaction.finish()
        verify(mockClient, never()).captureEnvelope(any())
    }

    @Test
    fun `when profiler is running and isAppStartTransaction is false, startTransaction does not interact with profiler`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        whenever(mockTransactionProfiler.isRunning).thenReturn(true)
        val scopes = generateScopes {
            it.profilesSampleRate = 1.0
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        val context = TransactionContext("name", "op")
        scopes.startTransaction(context, TransactionOptions().apply { isAppStartTransaction = false })
        verify(mockTransactionProfiler, never()).start()
        verify(mockTransactionProfiler, never()).bindTransaction(any())
    }

    @Test
    fun `when profiler is running and isAppStartTransaction is true, startTransaction binds current profile`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        whenever(mockTransactionProfiler.isRunning).thenReturn(true)
        val scopes = generateScopes {
            it.profilesSampleRate = 1.0
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        val context = TransactionContext("name", "op")
        val transaction = scopes.startTransaction(context, TransactionOptions().apply { isAppStartTransaction = true })
        verify(mockTransactionProfiler, never()).start()
        verify(mockTransactionProfiler).bindTransaction(eq(transaction))
    }

    @Test
    fun `when profiler is not running, startTransaction starts and binds current profile`() {
        val mockTransactionProfiler = mock<ITransactionProfiler>()
        whenever(mockTransactionProfiler.isRunning).thenReturn(false)
        val scopes = generateScopes {
            it.profilesSampleRate = 1.0
            it.setTransactionProfiler(mockTransactionProfiler)
        }
        val context = TransactionContext("name", "op")
        val transaction = scopes.startTransaction(context, TransactionOptions().apply { isAppStartTransaction = false })
        verify(mockTransactionProfiler).start()
        verify(mockTransactionProfiler).bindTransaction(eq(transaction))
    }
    //endregion

    //region startTransaction tests
    @Test
    fun `when startTransaction, creates transaction`() {
        val scopes = generateScopes()
        val contexts = TransactionContext("name", "op")

        val transaction = scopes.startTransaction(contexts)
        assertTrue(transaction is SentryTracer)
        assertEquals(contexts, transaction.root.spanContext)
    }

    @Test
    fun `when startTransaction with bindToScope set to false, transaction is not attached to the scope`() {
        val scopes = generateScopes()

        scopes.startTransaction("name", "op", TransactionOptions())

        scopes.configureScope {
            assertNull(it.span)
        }
    }

    @Test
    fun `when startTransaction without bindToScope set, transaction is not attached to the scope`() {
        val scopes = generateScopes()

        scopes.startTransaction("name", "op")

        scopes.configureScope {
            assertNull(it.span)
        }
    }

    @Test
    fun `when startTransaction with bindToScope set to true, transaction is attached to the scope`() {
        val scopes = generateScopes()

        val transaction = scopes.startTransaction("name", "op", TransactionOptions().also { it.isBindToScope = true })

        scopes.configureScope {
            assertEquals(transaction, it.span)
        }
    }

    @Test
    fun `when startTransaction and no tracing sampling is configured, event is not sampled`() {
        val scopes = generateScopes {
            it.tracesSampleRate = 0.0
        }

        val transaction = scopes.startTransaction("name", "op")
        assertFalse(transaction.isSampled!!)
    }

    @Test
    fun `when startTransaction and no profile sampling is configured, profile is not sampled`() {
        val scopes = generateScopes {
            it.tracesSampleRate = 1.0
            it.profilesSampleRate = 0.0
        }

        val transaction = scopes.startTransaction("name", "op")
        assertTrue(transaction.isSampled!!)
        assertFalse(transaction.isProfileSampled!!)
    }

    @Test
    fun `when startTransaction with parent sampled and no traces sampler provided, transaction inherits sampling decision`() {
        val scopes = generateScopes()
        val transactionContext = TransactionContext("name", "op")
        transactionContext.parentSampled = true
        val transaction = scopes.startTransaction(transactionContext)
        assertNotNull(transaction)
        assertNotNull(transaction.isSampled)
        assertTrue(transaction.isSampled!!)
    }

    @Test
    fun `when startTransaction with parent profile sampled and no profile sampler provided, transaction inherits profile sampling decision`() {
        val scopes = generateScopes()
        val transactionContext = TransactionContext("name", "op")
        transactionContext.setParentSampled(true, true)
        val transaction = scopes.startTransaction(transactionContext)
        assertTrue(transaction.isProfileSampled!!)
    }

    @Test
    fun `Scopes should close the sentry executor processor, profiler and performance collector on close call`() {
        val executor = mock<ISentryExecutorService>()
        val profiler = mock<ITransactionProfiler>()
        val backpressureMonitorMock = mock<IBackpressureMonitor>()
        val continuousProfiler = mock<IContinuousProfiler>()
        val performanceCollector = mock<CompositePerformanceCollector>()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            cacheDirPath = file.absolutePath
            executorService = executor
            setTransactionProfiler(profiler)
            compositePerformanceCollector = performanceCollector
            setContinuousProfiler(continuousProfiler)
            profileSessionSampleRate = 1.0
            backpressureMonitor = backpressureMonitorMock
        }
        val sut = createScopes(options)
        sut.close()
        verify(backpressureMonitorMock).close()
        verify(executor).close(any())
        verify(profiler).close()
        verify(continuousProfiler).close(eq(true))
        verify(performanceCollector).close()
    }

    @Test
    fun `Scopes with isRestarting true should close the sentry executor in the background`() {
        val executor = spy(DeferredExecutorService())
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            executorService = executor
        }
        val sut = createScopes(options)
        sut.close(true)
        verify(executor, never()).close(any())
        executor.runAll()
        verify(executor).close(any())
    }

    @Test
    fun `Scopes with isRestarting false should close the sentry executor in the background`() {
        val executor = mock<ISentryExecutorService>()
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            executorService = executor
        }
        val sut = createScopes(options)
        sut.close(false)
        verify(executor).close(any())
    }

    @Test
    fun `Scopes close should clear the scope`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }

        val sut = createScopes(options)
        sut.addBreadcrumb("Test")
        sut.startTransaction("test", "test.op", TransactionOptions().also { it.isBindToScope = true })
        sut.close()

        // we have to clone the scope, so its isEnabled returns true, but it's still built up from
        // the old scope preserving its data
        val clone = sut.forkedScopes("test")
        clone.bindClient(createSentryClientMock(enabled = true))
        var oldScope: IScope? = null
        clone.configureScope { scope -> oldScope = scope }
        assertNull(oldScope!!.transaction)
        assertTrue(oldScope!!.breadcrumbs.isEmpty())
    }

    @Test
    fun `when tracesSampleRate and tracesSampler are not set on SentryOptions, startTransaction returns NoOp`() {
        val scopes = generateScopes {
            it.tracesSampleRate = null
            it.tracesSampler = null
        }
        val transaction = scopes.startTransaction(TransactionContext("name", "op", TracesSamplingDecision(true)))
        assertTrue(transaction is NoOpTransaction)
    }

    @Test
    fun `when startTransaction, trace profile session is started`() {
        val scopes = generateScopes {
            it.tracesSampleRate = 1.0
            it.setContinuousProfiler(mockProfiler)
            it.profileSessionSampleRate = 1.0
            it.profileLifecycle = ProfileLifecycle.TRACE
        }

        val transaction = scopes.startTransaction("name", "op")
        assertTrue(transaction.isSampled!!)
        verify(mockProfiler).startProfiler(eq(ProfileLifecycle.TRACE), any())
    }

    @Test
    fun `when startTransaction, manual profile session is not started`() {
        val scopes = generateScopes {
            it.tracesSampleRate = 1.0
            it.setContinuousProfiler(mockProfiler)
            it.profileSessionSampleRate = 1.0
            it.profileLifecycle = ProfileLifecycle.MANUAL
        }

        val transaction = scopes.startTransaction("name", "op")
        assertTrue(transaction.isSampled!!)
        verify(mockProfiler, never()).startProfiler(any(), any())
    }

    @Test
    fun `when startTransaction not sampled, trace profile session is not started`() {
        val scopes = generateScopes {
            // If transaction is not sampled, profiler should not start
            it.tracesSampleRate = 0.0
            it.setContinuousProfiler(mockProfiler)
            it.profileSessionSampleRate = 1.0
            it.profileLifecycle = ProfileLifecycle.TRACE
        }
        val transaction = scopes.startTransaction("name", "op")
        transaction.spanContext.setSampled(false, false)
        assertFalse(transaction.isSampled!!)
        verify(mockProfiler, never()).startProfiler(any(), any())
    }
    //endregion

    //region getSpan tests
    @Test
    fun `when there is no active transaction, getSpan returns null`() {
        val scopes = generateScopes()
        assertNull(scopes.span)
    }

    @Test
    fun `when there is no active transaction, getTransaction returns null`() {
        val scopes = generateScopes()
        assertNull(scopes.transaction)
    }

    @Test
    fun `when there is active transaction bound to the scope, getTransaction and getSpan return active transaction`() {
        val scopes = generateScopes()
        val tx = scopes.startTransaction("aTransaction", "op")
        scopes.configureScope { it.transaction = tx }

        assertEquals(tx, scopes.transaction)
        assertEquals(tx, scopes.span)
    }

    @Test
    fun `when there is a transaction but the scopes is closed, getTransaction returns null`() {
        val scopes = generateScopes()
        scopes.startTransaction("name", "op")
        scopes.close()

        assertNull(scopes.transaction)
    }

    @Test
    fun `when there is active span within a transaction bound to the scope, getSpan returns active span`() {
        val scopes = generateScopes()
        val tx = scopes.startTransaction("aTransaction", "op")
        scopes.configureScope { it.setTransaction(tx) }
        scopes.configureScope { it.setTransaction(tx) }
        val span = tx.startChild("op")

        assertEquals(tx, scopes.transaction)
        assertEquals(span, scopes.span)
    }
    // endregion

    //region setSpanContext
    @Test
    fun `associates span context with throwable`() {
        val (scopes, mockClient) = getEnabledScopes()
        val transaction = scopes.startTransaction("aTransaction", "op")
        val span = transaction.startChild("op")
        val exception = RuntimeException()

        scopes.setSpanContext(exception, span, "tx-name")
        scopes.captureEvent(SentryEvent(exception))

        verify(mockClient).captureEvent(
            check {
                assertEquals(span.spanContext, it.contexts.trace)
            },
            anyOrNull(),
            anyOrNull()
        )
    }
    // endregion

    @Test
    fun `isCrashedLastRun does not delete native marker if auto session is enabled`() {
        val nativeMarker = File(hashedFolder(), EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        nativeMarker.mkdirs()
        nativeMarker.createNewFile()
        val scopes = generateScopes() as Scopes

        assertTrue(scopes.isCrashedLastRun!!)
        assertTrue(nativeMarker.exists())
    }

    @Test
    fun `isCrashedLastRun deletes the native marker if auto session is disabled`() {
        val nativeMarker = File(hashedFolder(), EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
        nativeMarker.mkdirs()
        nativeMarker.createNewFile()
        val scopes = generateScopes {
            it.isEnableAutoSessionTracking = false
        }

        assertTrue(scopes.isCrashedLastRun!!)
        assertFalse(nativeMarker.exists())
    }

    @Test
    fun `reportFullyDisplayed is ignored if TimeToFullDisplayTracing is disabled`() {
        var called = false
        val scopes = generateScopes {
            it.fullyDisplayedReporter.registerFullyDrawnListener {
                called = !called
            }
        }
        scopes.reportFullyDisplayed()
        assertFalse(called)
    }

    @Test
    fun `reportFullyDisplayed calls FullyDisplayedReporter if TimeToFullDisplayTracing is enabled`() {
        var called = false
        val scopes = generateScopes {
            it.isEnableTimeToFullDisplayTracing = true
            it.fullyDisplayedReporter.registerFullyDrawnListener {
                called = !called
            }
        }
        scopes.reportFullyDisplayed()
        assertTrue(called)
    }

    @Test
    fun `reportFullyDisplayed calls FullyDisplayedReporter only once`() {
        var called = false
        val scopes = generateScopes {
            it.isEnableTimeToFullDisplayTracing = true
            it.fullyDisplayedReporter.registerFullyDrawnListener {
                called = !called
            }
        }
        scopes.reportFullyDisplayed()
        assertTrue(called)
        scopes.reportFullyDisplayed()
        assertTrue(called)
    }

    @Test
    fun `continueTrace creates propagation context from headers and returns transaction context if performance enabled`() {
        val scopes = generateScopes()
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val transactionContext = scopes.continueTrace("$traceId-$parentSpanId-1", listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"))

        scopes.configureScope { scope ->
            assertEquals(traceId, scope.propagationContext.traceId)
            assertEquals(parentSpanId, scope.propagationContext.parentSpanId)
        }

        assertEquals(traceId, transactionContext!!.traceId)
        assertEquals(parentSpanId, transactionContext!!.parentSpanId)
    }

    @Test
    fun `continueTrace creates propagation context from headers and returns transaction context if performance enabled no sampled value`() {
        val scopes = generateScopes()
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val transactionContext = scopes.continueTrace("$traceId-$parentSpanId", listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"))

        scopes.configureScope { scope ->
            assertEquals(traceId, scope.propagationContext.traceId)
            assertEquals(parentSpanId, scope.propagationContext.parentSpanId)
        }

        assertEquals(traceId, transactionContext!!.traceId)
        assertEquals(parentSpanId, transactionContext!!.parentSpanId)
        assertEquals(null, transactionContext!!.parentSamplingDecision)
    }

    @Test
    fun `continueTrace creates new propagation context if header invalid and returns transaction context if performance enabled`() {
        val scopes = generateScopes()
        val traceId = SentryId()
        var propagationContextHolder = AtomicReference<PropagationContext>()

        scopes.configureScope { propagationContextHolder.set(it.propagationContext) }
        val propagationContextAtStart = propagationContextHolder.get()!!

        val transactionContext = scopes.continueTrace("invalid", listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"))

        scopes.configureScope { scope ->
            assertNotEquals(propagationContextAtStart.traceId, scope.propagationContext.traceId)
            assertNotEquals(propagationContextAtStart.parentSpanId, scope.propagationContext.parentSpanId)
            assertNotEquals(propagationContextAtStart.spanId, scope.propagationContext.spanId)

            assertEquals(scope.propagationContext.traceId, transactionContext!!.traceId)
            assertEquals(scope.propagationContext.parentSpanId, transactionContext!!.parentSpanId)
            assertEquals(scope.propagationContext.spanId, transactionContext!!.spanId)
        }
    }

    @Test
    fun `continueTrace creates propagation context from headers and returns null if performance disabled`() {
        val scopes = generateScopes { it.tracesSampleRate = null }
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val transactionContext = scopes.continueTrace("$traceId-$parentSpanId-1", listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"))

        scopes.configureScope { scope ->
            assertEquals(traceId, scope.propagationContext.traceId)
            assertEquals(parentSpanId, scope.propagationContext.parentSpanId)
        }

        assertNull(transactionContext)
    }

    @Test
    fun `continueTrace creates new propagation context if header invalid and returns null if performance disabled`() {
        val scopes = generateScopes { it.tracesSampleRate = null }
        val traceId = SentryId()
        var propagationContextHolder = AtomicReference<PropagationContext>()

        scopes.configureScope { propagationContextHolder.set(it.propagationContext) }
        val propagationContextAtStart = propagationContextHolder.get()!!

        val transactionContext = scopes.continueTrace("invalid", listOf("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET"))

        scopes.configureScope { scope ->
            assertNotEquals(propagationContextAtStart.traceId, scope.propagationContext.traceId)
            assertNotEquals(propagationContextAtStart.parentSpanId, scope.propagationContext.parentSpanId)
            assertNotEquals(propagationContextAtStart.spanId, scope.propagationContext.spanId)
        }

        assertNull(transactionContext)
    }

    // region replay event tests
    @Test
    fun `when captureReplay is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()
        sut.close()

        sut.captureReplay(SentryReplayEvent(), Hint())
        verify(mockClient, never()).captureReplayEvent(any(), any(), any<Hint>())
    }

    @Test
    fun `when captureReplay is called with a valid argument, captureReplay on the client should be called`() {
        val (sut, mockClient) = getEnabledScopes()

        val event = SentryReplayEvent()
        val hints = HintUtils.createWithTypeCheckHint({})
        sut.captureReplay(event, hints)
        verify(mockClient).captureReplayEvent(eq(event), any(), eq(hints))
    }
    // endregion replay event tests

    @Test
    fun `is considered enabled if client is enabled()`() {
        val scopes = generateScopes() as Scopes
        val client = mock<ISentryClient>()
        whenever(client.isEnabled).thenReturn(true)
        scopes.bindClient(client)
        assertTrue(scopes.isEnabled)
    }

    @Test
    fun `is considered disabled if client is disabled()`() {
        val scopes = generateScopes() as Scopes
        val client = mock<ISentryClient>()
        whenever(client.isEnabled).thenReturn(false)
        scopes.bindClient(client)
        assertFalse(scopes.isEnabled)
    }

    @Test
    fun `creating a transaction with an ignored origin noops`() {
        val scopes = generateScopes {
            it.setIgnoredSpanOrigins(listOf("ignored.span.origin"))
        }

        val transactionContext = TransactionContext("transaction-name", "transaction-op")
        val transactionOptions = TransactionOptions().also {
            it.origin = "ignored.span.origin"
            it.isBindToScope = true
        }

        val transaction = scopes.startTransaction(transactionContext, transactionOptions)
        assertTrue(transaction.isNoOp)
        scopes.configureScope { assertNull(it.transaction) }
    }

    @Test
    fun `creating a transaction with a non ignored origin creates the transaction`() {
        val scopes = generateScopes {
            it.setIgnoredSpanOrigins(listOf("ignored.span.origin"))
        }

        val transactionContext = TransactionContext("transaction-name", "transaction-op")
        val transactionOptions = TransactionOptions().also {
            it.origin = "other.span.origin"
            it.isBindToScope = true
        }

        val transaction = scopes.startTransaction(transactionContext, transactionOptions)
        assertFalse(transaction.isNoOp)
        scopes.configureScope { assertSame(transaction, it.transaction) }
    }

    @Test
    fun `creating a transaction with origin sets the origin on the transaction context`() {
        val scopes = generateScopes()

        val transactionContext = TransactionContext("transaction-name", "transaction-op")
        val transactionOptions = TransactionOptions().also {
            it.origin = "other.span.origin"
        }

        val transaction = scopes.startTransaction(transactionContext, transactionOptions)
        assertEquals("other.span.origin", transaction.spanContext.origin)
    }

    //region profileSession

    @Test
    fun `startProfiler starts the continuous profiler`() {
        val profiler = mock<IContinuousProfiler>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
        }
        scopes.startProfiler()
        verify(profiler).startProfiler(eq(ProfileLifecycle.MANUAL), any())
    }

    @Test
    fun `startProfiler logs instructions if continuous profiling is disabled`() {
        val profiler = mock<IContinuousProfiler>()
        val logger = mock<ILogger>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
            it.profilesSampleRate = 1.0
            it.setLogger(logger)
            it.isDebug = true
        }
        scopes.startProfiler()
        verify(profiler, never()).startProfiler(eq(ProfileLifecycle.MANUAL), any())
        verify(logger).log(eq(SentryLevel.WARNING), eq("Continuous Profiling is not enabled. Set profilesSampleRate and profilesSampler to null to enable it."))
    }

    @Test
    fun `startProfiler is ignored on trace lifecycle`() {
        val profiler = mock<IContinuousProfiler>()
        val logger = mock<ILogger>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
            it.profileLifecycle = ProfileLifecycle.TRACE
            it.setLogger(logger)
            it.isDebug = true
        }
        scopes.startProfiler()
        verify(logger).log(eq(SentryLevel.WARNING), eq("Profiling lifecycle is %s. Profiling cannot be started manually."), eq(ProfileLifecycle.TRACE.name))
        verify(profiler, never()).startProfiler(any(), any())
    }

    @Test
    fun `stopProfiler stops the continuous profiler`() {
        val profiler = mock<IContinuousProfiler>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
        }
        scopes.stopProfiler()
        verify(profiler).stopProfiler(eq(ProfileLifecycle.MANUAL))
    }

    @Test
    fun `stopProfiler logs instructions if continuous profiling is disabled`() {
        val profiler = mock<IContinuousProfiler>()
        val logger = mock<ILogger>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
            it.profilesSampleRate = 1.0
            it.setLogger(logger)
            it.isDebug = true
        }
        scopes.stopProfiler()
        verify(profiler, never()).stopProfiler(eq(ProfileLifecycle.MANUAL))
        verify(logger).log(eq(SentryLevel.WARNING), eq("Continuous Profiling is not enabled. Set profilesSampleRate and profilesSampler to null to enable it."))
    }

    @Test
    fun `stopProfiler is ignored on trace lifecycle`() {
        val profiler = mock<IContinuousProfiler>()
        val logger = mock<ILogger>()
        val scopes = generateScopes {
            it.setContinuousProfiler(profiler)
            it.profileSessionSampleRate = 1.0
            it.profileLifecycle = ProfileLifecycle.TRACE
            it.setLogger(logger)
            it.isDebug = true
        }
        scopes.stopProfiler()
        verify(logger).log(eq(SentryLevel.WARNING), eq("Profiling lifecycle is %s. Profiling cannot be stopped manually."), eq(ProfileLifecycle.TRACE.name))
        verify(profiler, never()).stopProfiler(any())
    }

    //endregion

    //region logs

    @Test
    fun `when captureLog is called on disabled client, do nothing`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }
        sut.close()

        sut.logger().warn("test message")
        verify(mockClient, never()).captureLog(any(), anyOrNull())
    }

    @Test
    fun `when logging is not enabled, do nothing`() {
        val (sut, mockClient) = getEnabledScopes()

        sut.logger().warn("test message")
        verify(mockClient, never()).captureLog(any(), anyOrNull())
    }

    @Test
    fun `capturing null log does nothing`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().warn(null)
        verify(mockClient, never()).captureLog(any(), anyOrNull())
    }

    @Test
    fun `creating trace log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().trace("trace log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("trace log message", it.body)
                assertEquals(SentryLogLevel.TRACE, it.level)
                assertEquals(1, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating debug log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().debug("debug log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("debug log message", it.body)
                assertEquals(SentryLogLevel.DEBUG, it.level)
                assertEquals(5, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating a info log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().info("info log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("info log message", it.body)
                assertEquals(SentryLogLevel.INFO, it.level)
                assertEquals(9, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating warn log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().warn("warn log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("warn log message", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating error log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().error("error log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("error log message", it.body)
                assertEquals(SentryLogLevel.ERROR, it.level)
                assertEquals(17, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating fatal log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().fatal("fatal log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("fatal log message", it.body)
                assertEquals(SentryLogLevel.FATAL, it.level)
                assertEquals(21, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with format string works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
            it.environment = "testenv"
            it.release = "1.0"
            it.serverName = "srv1"
        }

        sut.logger().log(SentryLogLevel.WARN, "log %s", "arg1")

        verify(mockClient).captureLog(
            check {
                assertEquals("log arg1", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val template = it.attributes?.get("sentry.message.template")!!
                assertEquals("log %s", template.value)
                assertEquals("string", template.type)

                val param0 = it.attributes?.get("sentry.message.parameter.0")!!
                assertEquals("arg1", param0.value)
                assertEquals("string", param0.type)

                val environment = it.attributes?.get("sentry.environment")!!
                assertEquals("testenv", environment.value)
                assertEquals("string", environment.type)

                val release = it.attributes?.get("sentry.release")!!
                assertEquals("1.0", release.value)
                assertEquals("string", release.type)

                val server = it.attributes?.get("server.address")!!
                assertEquals("srv1", server.value)
                assertEquals("string", server.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with timestamp works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, SentryLongDate(123), "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)
                assertEquals(0.000000123, it.timestamp)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with attributes from map works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, SentryLogParameters.create(SentryAttributes.fromMap(mapOf("attrname1" to "attrval1"))), "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val attr1 = it.attributes?.get("attrname1")!!
                assertEquals("attrval1", attr1.value)
                assertEquals("string", attr1.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with attributes works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(
            SentryLogLevel.WARN,
            SentryLogParameters.create(
                SentryAttributes.of(
                    SentryAttribute.stringAttribute("strattr", "strval"),
                    SentryAttribute.booleanAttribute("boolattr", true),
                    SentryAttribute.integerAttribute("intattr", 17),
                    SentryAttribute.doubleAttribute("doubleattr", 3.8),
                    SentryAttribute.named("namedstrattr", "namedstrval"),
                    SentryAttribute.named("namedboolattr", false),
                    SentryAttribute.named("namedintattr", 18),
                    SentryAttribute.named("nameddoubleattr", 4.9)
                )
            ),
            "log message"
        )

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val strattr = it.attributes?.get("strattr")!!
                assertEquals("strval", strattr.value)
                assertEquals("string", strattr.type)

                val boolattr = it.attributes?.get("boolattr")!!
                assertEquals(true, boolattr.value)
                assertEquals("boolean", boolattr.type)

                val intattr = it.attributes?.get("intattr")!!
                assertEquals(17, intattr.value)
                assertEquals("integer", intattr.type)

                val doubleattr = it.attributes?.get("doubleattr")!!
                assertEquals(3.8, doubleattr.value)
                assertEquals("double", doubleattr.type)

                val namedstrattr = it.attributes?.get("namedstrattr")!!
                assertEquals("namedstrval", namedstrattr.value)
                assertEquals("string", namedstrattr.type)

                val namedboolattr = it.attributes?.get("namedboolattr")!!
                assertEquals(false, namedboolattr.value)
                assertEquals("boolean", namedboolattr.type)

                val namedintattr = it.attributes?.get("namedintattr")!!
                assertEquals(18, namedintattr.value)
                assertEquals("integer", namedintattr.type)

                val nameddoubleattr = it.attributes?.get("nameddoubleattr")!!
                assertEquals(4.9, nameddoubleattr.value)
                assertEquals("double", nameddoubleattr.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with attributes and timestamp works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, SentryLogParameters.create(SentryLongDate(123), SentryAttributes.of(SentryAttribute.named("attrname1", "attrval1"))), "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)
                assertEquals(0.000000123, it.timestamp)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val attr1 = it.attributes?.get("attrname1")!!
                assertEquals("attrval1", attr1.value)
                assertEquals("string", attr1.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with attributes and timestamp and format string works`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, SentryLogParameters.create(SentryLongDate(123), SentryAttributes.of(SentryAttribute.named("attrname1", "attrval1"))), "log %s %d %b %.0f", "message", 1, true, 3.2)

        verify(mockClient).captureLog(
            check {
                assertEquals("log message 1 true 3", it.body)
                assertEquals(0.000000123, it.timestamp)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val attr1 = it.attributes?.get("attrname1")!!
                assertEquals("attrval1", attr1.value)
                assertEquals("string", attr1.type)

                val template = it.attributes?.get("sentry.message.template")!!
                assertEquals("log %s %d %b %.0f", template.value)
                assertEquals("string", template.type)

                val param0 = it.attributes?.get("sentry.message.parameter.0")!!
                assertEquals("message", param0.value)
                assertEquals("string", param0.type)

                val param1 = it.attributes?.get("sentry.message.parameter.1")!!
                assertEquals(1, param1.value)
                assertEquals("integer", param1.type)

                val param2 = it.attributes?.get("sentry.message.parameter.2")!!
                assertEquals(true, param2.value)
                assertEquals("boolean", param2.type)

                val param3 = it.attributes?.get("sentry.message.parameter.3")!!
                assertEquals(3.2, param3.value)
                assertEquals("double", param3.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `creating log with without args does not add template attribute`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, "log %s")

        verify(mockClient).captureLog(
            check {
                assertEquals("log %s", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val template = it.attributes?.get("sentry.message.template")
                assertNull(template)

                val param0 = it.attributes?.get("sentry.message.parameter.0")
                assertNull(param0)
            },
            anyOrNull()
        )
    }

    @Test
    fun `captures format string on format error`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.logger().log(SentryLogLevel.WARN, "log %d", "arg1")

        verify(mockClient).captureLog(
            check {
                assertEquals("log %d", it.body)
                assertEquals(SentryLogLevel.WARN, it.level)
                assertEquals(13, it.severityNumber)

                val template = it.attributes?.get("sentry.message.template")!!
                assertEquals("log %d", template.value)
                assertEquals("string", template.type)

                val param0 = it.attributes?.get("sentry.message.parameter.0")!!
                assertEquals("arg1", param0.value)
                assertEquals("string", param0.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `adds user fields to log attributes if sendDefaultPii is true`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
            it.isSendDefaultPii = true
        }

        sut.configureScope { scope ->
            scope.user = User().also {
                it.id = "usrid"
                it.username = "usrname"
                it.email = "user@sentry.io"
            }
        }
        sut.logger().log(SentryLogLevel.WARN, "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)

                val userId = it.attributes?.get("user.id")!!
                assertEquals("usrid", userId.value)
                assertEquals("string", userId.type)

                val userName = it.attributes?.get("user.name")!!
                assertEquals("usrname", userName.value)
                assertEquals("string", userName.type)

                val userEmail = it.attributes?.get("user.email")!!
                assertEquals("user@sentry.io", userEmail.value)
                assertEquals("string", userEmail.type)
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not add user fields to log attributes by default`() {
        val (sut, mockClient) = getEnabledScopes {
            it.logs.isEnabled = true
        }

        sut.configureScope { scope ->
            scope.user = User().also {
                it.id = "usrid"
                it.username = "usrname"
                it.email = "user@sentry.io"
            }
        }
        sut.logger().log(SentryLogLevel.WARN, "log message")

        verify(mockClient).captureLog(
            check {
                assertEquals("log message", it.body)

                assertNull(it.attributes?.get("user.id"))
                assertNull(it.attributes?.get("user.name"))
                assertNull(it.attributes?.get("user.email"))
            },
            anyOrNull()
        )
    }

    //endregion

    @Test
    fun `null tags do not cause NPE`() {
        val scopes = generateScopes()
        scopes.setTag(null, null)
        scopes.setTag("k", null)
        scopes.setTag(null, "v")
        scopes.removeTag(null)
        assertTrue(scopes.scope.tags.isEmpty())
        assertTrue(scopes.isolationScope.tags.isEmpty())
        assertTrue(scopes.globalScope.tags.isEmpty())
    }

    @Test
    fun `null extras do not cause NPE`() {
        val scopes = generateScopes()
        scopes.setExtra(null, null)
        scopes.setExtra("k", null)
        scopes.setExtra(null, "v")
        scopes.removeExtra(null)
        assertTrue(scopes.scope.extras.isEmpty())
        assertTrue(scopes.isolationScope.extras.isEmpty())
        assertTrue(scopes.globalScope.extras.isEmpty())
    }

    private val dsnTest = "https://key@sentry.io/proj"

    private fun generateScopes(optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions>? = null): IScopes {
        val options = SentryOptions().apply {
            dsn = dsnTest
            cacheDirPath = file.absolutePath
            setSerializer(mock())
            tracesSampleRate = 1.0
        }
        optionsConfiguration?.configure(options)
        return createScopes(options)
    }

    private fun getEnabledScopes(optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions>? = null): Triple<Scopes, ISentryClient, ILogger> {
        val logger = mock<ILogger>()

        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.tracesSampleRate = 1.0
        options.isDebug = true
        options.setLogger(logger)
        optionsConfiguration?.configure(options)

        val sut = createScopes(options)
        val mockClient = createSentryClientMock()
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
                expectedContext.userId == actual.userId
        }
    }
}

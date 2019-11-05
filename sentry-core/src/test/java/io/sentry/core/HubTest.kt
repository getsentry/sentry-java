package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.protocol.SentryId
import io.sentry.core.protocol.User
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Queue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class HubTest {

    //region clone tests
    @Test
    fun `when cloning Scope it returns the same values`() {
        val scope = Scope(10)
        scope.setExtra("extra", "extra")
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        scope.addBreadcrumb(breadcrumb)
        scope.level = SentryLevel.DEBUG
        scope.transaction = "transaction"
        scope.fingerprint.add("fingerprint")
        scope.tags["tags"] = "tags"
        val user = User()
        user.email = "a@a.com"
        scope.user = user

        val clone = scope.clone()
        assertNotNull(clone)
        assertNotSame(scope, clone)
        assertEquals("extra", clone.extras["extra"])
        assertEquals("message", clone.breadcrumbs.first().message)
        assertEquals("transaction", clone.transaction)
        assertEquals("fingerprint", clone.fingerprint.first())
        assertEquals("tags", clone.tags["tags"])
        assertEquals("a@a.com", clone.user.email)
    }

    @Test
    @Ignore("it's a shallow copy and we need a deep-copy") // TODO: https://www.baeldung.com/java-deep-copy
    fun `when cloning Scope it returns different instances`() {
        val scope = Scope(10)
        scope.setExtra("extra", "extra")
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        scope.addBreadcrumb(breadcrumb)
        scope.level = SentryLevel.DEBUG
        scope.transaction = "transaction"
        scope.fingerprint.add("fingerprint")
        scope.tags["tags"] = "tags"
        val user = User()
        user.email = "a@a.com"
        scope.user = user

        val clone = scope.clone()
        assertNotNull(clone)
        assertNotSame(scope, clone)
        assertNotSame(scope.extras, clone.extras)
        assertNotSame(scope.breadcrumbs, clone.breadcrumbs)
        assertNotSame(scope.breadcrumbs.first(), clone.breadcrumbs.first())
        assertNotSame(scope.transaction, clone.transaction)
        assertNotSame(scope.fingerprint, clone.fingerprint)
        assertNotSame(scope.fingerprint.first(), clone.fingerprint.first())
        assertNotSame(scope.tags, clone.tags)
        assertNotSame(scope.user, clone.user)
    }
    //endregion

    @Test
    fun `when hub is initialized, integrations are registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        val expected = Hub(options)
        verify(integrationMock).register(expected, options)
    }

    @Test
    fun `when hub is initialized, breadcrumbs are capped as per options`() {
        val options = SentryOptions()
        options.maxBreadcrumbs = 5
        options.dsn = "https://key@sentry.io/proj"
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
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback {
            _: Breadcrumb, _: Any? -> null }
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        sut.addBreadcrumb(Breadcrumb(), null)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(0, breadcrumbs!!.size)
    }

    @Test
    fun `when beforeBreadcrumb modifies crumb, crumb is stored modified`() {
        val options = SentryOptions()
        val expected = "expected"
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb: Breadcrumb, _: Any? -> breadcrumb.message = expected; breadcrumb; }
        options.dsn = "https://key@sentry.io/proj"
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
        options.beforeBreadcrumb = null
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val expected = Breadcrumb()
        sut.addBreadcrumb(expected)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.single())
    }

    @Test
    fun `when beforeSend throws an exception, breadcrumb adds an entry to the data field with exception message and stacktrace`() {
        val exception = Exception("test")
        val sw = StringWriter()
        exception.printStackTrace(PrintWriter(sw))
        val stacktrace = sw.toString()

        val options = SentryOptions()
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _: Breadcrumb, _: Any? -> throw exception }
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)

        val actual = Breadcrumb()
        sut.addBreadcrumb(actual)

        assertEquals("test", actual.data["sentry:message"])
        assertEquals(stacktrace, actual.data["sentry:stacktrace"])
    }

    @Test
    fun `when initialized, lastEventId is empty`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when addBreadcrumb is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.close()
        sut.addBreadcrumb(Breadcrumb())
        assertTrue(breadcrumbs!!.isEmpty())
    }

    @Test
    fun `when flush is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.flush(1000)
        verify(mockClient, never()).flush(1000)
    }

    @Test
    fun `when flush is called, client flush gets called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.flush(1000)
        verify(mockClient).flush(1000)
    }

    //region captureEvent tests
    @Test
    fun `when captureEvent is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        sut.captureEvent(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureEvent is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureEvent(SentryEvent())
        verify(mockClient, never()).captureEvent(any(), any())
    }

    @Test
    fun `when captureEvent is called with a valid argument, captureEvent on the client should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureEvent(SentryEvent())
        verify(mockClient, times(1)).captureEvent(any(), any())
    }
    //endregion

    //region captureMessage tests
    @Test
    fun `when captureMessage is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        sut.captureMessage(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureMessage is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureMessage("test")
        verify(mockClient, never()).captureMessage(any(), any())
    }

    @Test
    fun `when captureMessage is called with a valid message, captureMessage on the client should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureMessage("test")
        verify(mockClient, times(1)).captureMessage(any(), any())
    }
    //endregion

    //region captureException tests
    @Test
    fun `when captureException is called and exception is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        sut.captureException(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureException is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureException(Throwable())
        verify(mockClient, never()).captureException(any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument, captureException on the client should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureException(Throwable())
        verify(mockClient, times(1)).captureException(any(), any())
    }
    //endregion

    //region close tests
    @Test
    fun `when close is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.close()
        verify(mockClient, times(1)).close() // 1 to close, but next one wont be recorded
    }

    @Test
    fun `when close is called and client is alive, close on the client should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.close()
        verify(mockClient, times(1)).close()
    }
    //endregion

    //region withScope tests
    @Test
    fun `when withScope is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.withScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when withScope is called with alive client, run should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()

        sut.withScope(scopeCallback)
        verify(scopeCallback, times(1)).run(any())
    }
    //endregion

    //region configureScope tests
    @Test
    fun `when configureScope is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when configureScope is called with alive client, run should be called`() {
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, times(1)).run(any())
    }
    //endregion

    @Test
    fun `when integration is registered, hub is enabled`() {
        val mock = mock<Integration>()
        val options = SentryOptions().apply {
            addIntegration(mock)
            dsn = "https://key@sentry.io/proj"
        }
        doAnswer {
            val hub = it.arguments[0] as IHub
            assertTrue(hub.isEnabled)
        }.whenever(mock).register(any(), eq(options))
        Hub(options)
        verify(mock, times(1)).register(any(), eq(options))
    }
}

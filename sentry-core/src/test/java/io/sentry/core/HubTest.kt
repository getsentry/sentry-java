package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class HubTest {

    @Test
    fun `when cloning Scope it returns the same values`() {
        val scope = Scope()
        scope.extra["test"] = "test"
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "test"
        scope.breadcrumbs.add(breadcrumb)
        scope.level = SentryLevel.DEBUG
        scope.transaction = "test"
        scope.fingerprint.add("test")
        scope.tags["test"] = "test"
        val user = User()
        user.email = "a@a.com"
        scope.user = user

        val clone = scope.clone()
        assertNotNull(clone)
        assertNotSame(scope, clone)
        assertEquals("test", clone.extra["test"])
        assertEquals("test", clone.breadcrumbs[0].message)
        assertEquals("test", scope.transaction)
        assertEquals("test", scope.fingerprint[0])
        assertEquals("test", clone.tags["test"])
        assertEquals("a@a.com", clone.user.email)
    }

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
    fun `when beforeBreadcrumb returns null, crumb is dropped`() {
        val options = SentryOptions()
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { null }
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        sut.addBreadcrumb(Breadcrumb())
        var breadcrumbs: List<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(0, breadcrumbs!!.size)
    }

    @Test
    fun `when beforeBreadcrumb modifies crumb, crumb is stored modified`() {
        val options = SentryOptions()
        val expected = "expected"
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { it.message = expected; it }
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        var crumb = Breadcrumb()
        crumb.message = "original"
        sut.addBreadcrumb(crumb)
        var breadcrumbs: List<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.first().message)
    }

    @Test
    fun `when beforeBreadcrumb is null, crumb is stored`() {
        val options = SentryOptions()
        options.beforeBreadcrumb = null
        options.dsn = "https://key@sentry.io/proj"
        val sut = Hub(options)
        var expected = Breadcrumb()
        sut.addBreadcrumb(expected)
        var breadcrumbs: List<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.single())
    }
}

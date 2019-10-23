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
    fun `when hub is initialized, integrations are registed`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        val expected = Hub(options)
        verify(integrationMock).register(expected, options)
    }
}

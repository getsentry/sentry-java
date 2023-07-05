package io.sentry

import io.sentry.protocol.App
import io.sentry.protocol.User
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertTrue

class InternalSentrySdkTest {
    @Test
    fun `serializeScope correctly creates top level map`() {
        val options = SentryOptions()
        val scope = Scope(options)

        scope.user = User().apply {
            name = "John"
        }
        scope.addBreadcrumb(Breadcrumb.ui("ui.click", "button_login"))
        scope.contexts.setApp(
            App().apply {
                appName = "Example App"
            }
        )
        scope.setTag("variant", "yellow")

        val serializedScope = InternalSentrySdk.serializeScope(scope)

        assertTrue(serializedScope.containsKey("user"))
        assertTrue(serializedScope.containsKey("contexts"))
        assertTrue(serializedScope.containsKey("tags"))
        assertTrue(serializedScope.containsKey("extras"))
        assertTrue(serializedScope.containsKey("fingerprint"))
        assertTrue(serializedScope.containsKey("level"))
        assertTrue(serializedScope.containsKey("breadcrumbs"))
    }

    @Test
    fun `serializeScope returns empty map in case scope is null`() {
        val serializedScope = InternalSentrySdk.serializeScope(null)
        assertTrue(serializedScope.isEmpty())
    }

    @Test
    fun `serializeScope returns empty map in case scope serialization fails`() {
        val scope = mock<Scope>()
        val options = SentryOptions()
        whenever(scope.options).thenReturn(options)
        whenever(scope.user).thenThrow(IllegalStateException("something is off"))

        val serializedScope = InternalSentrySdk.serializeScope(scope)
        assertTrue(serializedScope.isEmpty())
    }
}

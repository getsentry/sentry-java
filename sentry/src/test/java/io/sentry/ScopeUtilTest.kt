package io.sentry

import io.sentry.protocol.App
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertTrue

class ScopeUtilTest {
    @Test
    fun `serializing scopes correctly creates map`() {
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

        val serializedScope = ScopeUtil.serialize(scope)

        assertTrue(serializedScope.containsKey("user"))
        assertTrue(serializedScope.containsKey("contexts"))
        assertTrue(serializedScope.containsKey("tags"))
        assertTrue(serializedScope.containsKey("extras"))
        assertTrue(serializedScope.containsKey("fingerprint"))
        assertTrue(serializedScope.containsKey("level"))
        assertTrue(serializedScope.containsKey("breadcrumbs"))
    }
}

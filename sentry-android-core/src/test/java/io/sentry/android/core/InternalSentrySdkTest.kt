package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Hub
import io.sentry.NoOpHub
import io.sentry.Scope
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.App
import io.sentry.protocol.Contexts
import io.sentry.protocol.User
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class InternalSentrySdkTest {

    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        DeviceInfoUtil.resetInstance()
    }

    @Test
    fun `current scope returns null when hub is no-op`() {
        Sentry.setCurrentHub(NoOpHub.getInstance())
        val scope = InternalSentrySdk.getCurrentScope()
        assertNull(scope)
    }

    @Test
    fun `current scope returns obj when hub is active`() {
        Sentry.setCurrentHub(
            Hub(
                SentryOptions().apply {
                    dsn = "https://key@uri/1234567"
                }
            )
        )
        val scope = InternalSentrySdk.getCurrentScope()
        assertNotNull(scope)
    }

    @Test
    fun `serializeScope correctly creates top level map`() {
        val options = SentryAndroidOptions()
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

        val serializedScope = InternalSentrySdk.serializeScope(
            context,
            options,
            scope
        )

        assertTrue(serializedScope.containsKey("user"))
        assertTrue(serializedScope.containsKey("contexts"))
        assertTrue((serializedScope["contexts"] as Map<*, *>).containsKey("device"))

        assertTrue(serializedScope.containsKey("tags"))
        assertTrue(serializedScope.containsKey("extras"))
        assertTrue(serializedScope.containsKey("fingerprint"))
        assertTrue(serializedScope.containsKey("level"))
        assertTrue(serializedScope.containsKey("breadcrumbs"))
    }

    @Test
    fun `serializeScope returns empty map in case scope is null`() {
        val options = SentryAndroidOptions()
        val serializedScope = InternalSentrySdk.serializeScope(context, options, null)
        assertTrue(serializedScope.isEmpty())
    }

    @Test
    fun `serializeScope returns empty map in case scope serialization fails`() {
        val options = SentryAndroidOptions()
        val scope = mock<Scope>()

        whenever(scope.contexts).thenReturn(Contexts())
        whenever(scope.user).thenThrow(IllegalStateException("something is off"))

        val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
        assertTrue(serializedScope.isEmpty())
    }

    @Test
    fun `serializeScope provides fallback user if none is set`() {
        val options = SentryAndroidOptions()
        val scope = Scope(options)
        scope.user = null

        val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
        assertTrue((serializedScope["user"] as Map<*, *>).containsKey("id"))
    }

    @Test
    fun `serializeScope does not override user-id`() {
        val options = SentryAndroidOptions()
        val scope = Scope(options)
        scope.user = User().apply { id = "abc" }

        val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
        assertEquals("abc", (serializedScope["user"] as Map<*, *>)["id"])
    }

    @Test
    fun `serializeScope provides fallback app data if none is set`() {
        val options = SentryAndroidOptions()
        val scope = Scope(options)
        scope.setContexts("app", null)

        val serializedScope = InternalSentrySdk.serializeScope(context, options, scope)
        assertTrue(((serializedScope["contexts"] as Map<*, *>)["app"] as Map<*, *>).containsKey("app_name"))
    }
}

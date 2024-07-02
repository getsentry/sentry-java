package io.sentry.android.ndk

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.JsonSerializer
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.User
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class NdkScopeObserverTest {

    private class Fixture {
        val nativeScope = mock<INativeScope>()
        val options = SentryOptions().apply {
            setSerializer(JsonSerializer(mock()))
        }

        fun getSut(): NdkScopeObserver {
            return NdkScopeObserver(options, nativeScope)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `set tag forwards call to scope sync`() {
        val sut = fixture.getSut()

        sut.setTag("a", "b")

        verify(fixture.nativeScope).setTag("a", "b")
    }

    @Test
    fun `remove tag forwards call to scope sync`() {
        val sut = fixture.getSut()

        sut.removeTag("a")

        verify(fixture.nativeScope).removeTag("a")
    }

    @Test
    fun `set extra forwards call to scope sync`() {
        val sut = fixture.getSut()

        sut.setExtra("a", "b")

        verify(fixture.nativeScope).setExtra("a", "b")
    }

    @Test
    fun `remove extra forwards call to scope sync`() {
        val sut = fixture.getSut()

        sut.removeExtra("a")

        verify(fixture.nativeScope).removeExtra("a")
    }

    @Test
    fun `set user forwards call to scope sync`() {
        val sut = fixture.getSut()

        val user = User().apply {
            id = "id"
            email = "email"
            ipAddress = "ip"
            username = "username"
        }
        sut.setUser(user)

        verify(fixture.nativeScope).setUser(eq(user.id), eq(user.email), eq(user.ipAddress), eq(user.username))
    }

    @Test
    fun `remove user forwards call to scope sync`() {
        val sut = fixture.getSut()

        sut.setUser(null)

        verify(fixture.nativeScope).removeUser()
    }

    @Test
    fun `set breadcrumb forwards call to scope sync`() {
        val sut = fixture.getSut()

        val breadcrumb = Breadcrumb().apply {
            level = SentryLevel.DEBUG
            message = "message"
            category = "category"
            setData("a", "b")
            type = "type"
        }
        val timestamp = DateUtils.getTimestamp(breadcrumb.timestamp)
        val data = "{\"a\":\"b\"}"

        sut.addBreadcrumb(breadcrumb)

        verify(fixture.nativeScope).addBreadcrumb(
            eq("debug"),
            eq(breadcrumb.message),
            eq(breadcrumb.category),
            eq(breadcrumb.type),
            eq(timestamp),
            eq(data)
        )
    }
}

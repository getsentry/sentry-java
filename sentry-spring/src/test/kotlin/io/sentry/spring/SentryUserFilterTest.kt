package io.sentry.spring

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.protocol.User
import javax.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SentryUserFilterTest {
    class Fixture {
        val hub = mock<IHub>()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = mock<FilterChain>()
        val options = SentryOptions()
        val scope = Scope(options)

        fun getSut(isSendDefaultPii: Boolean = false, userProviders: List<SentryUserProvider>): SentryUserFilter {
            val options = SentryOptions()
            options.isSendDefaultPii = isSendDefaultPii
            whenever(hub.options).thenReturn(options)
            whenever(hub.configureScope(any())).thenAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
            return SentryUserFilter(hub, userProviders)
        }
    }

    private val fixture = Fixture()

    private val sampleUser = User().apply {
        username = "john.doe"
        id = "user-id"
        ipAddress = "192.168.0.1"
        email = "john.doe@example.com"
        others = mapOf("key" to "value")
    }

    @Test
    fun `sets provided user data on the scope`() {
        val filter = fixture.getSut(userProviders = listOf(SentryUserProvider {
            sampleUser
        }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertEquals(sampleUser, it)
        }
    }

    @Test
    fun `when processor returns empty User, user data is not changed`() {
        val filter = fixture.getSut(userProviders = listOf(SentryUserProvider {
            sampleUser
        }, SentryUserProvider { User() }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertEquals(sampleUser, it)
        }
    }

    @Test
    fun `when processor returns null, user data is not changed`() {
        val filter = fixture.getSut(userProviders = listOf(SentryUserProvider {
            sampleUser
        }, SentryUserProvider { null }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertEquals(sampleUser, it)
        }
    }

    @Test
    fun `merges user#others with existing user#others set on SentryEvent`() {
        val filter = fixture.getSut(userProviders = listOf(SentryUserProvider {
            User().apply {
                others = mapOf("key" to "value")
            }
        }, SentryUserProvider {
            User().apply {
                others = mapOf("new-key" to "new-value")
            }
        }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertEquals(mapOf("key" to "value", "new-key" to "new-value"), it.others)
        }
    }

    @Test
    fun `when isSendDefaultPii is true and user is set with custom ip address, user ip is unchanged`() {
        val filter = fixture.getSut(isSendDefaultPii = true, userProviders = listOf(SentryUserProvider {
            User().apply {
                ipAddress = "192.168.0.1"
            }
        }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertEquals("192.168.0.1", it.ipAddress)
        }
    }

    @Test
    fun `when isSendDefaultPii is true and user is set with {{auto}} ip address, user ip is set to null`() {
        val filter = fixture.getSut(isSendDefaultPii = true, userProviders = listOf(SentryUserProvider {
            User().apply {
                ipAddress = "{{auto}}"
            }
        }))

        filter.doFilter(fixture.request, fixture.response, fixture.chain)

        assertNotNull(fixture.scope.user) {
            assertNull(it.ipAddress)
        }
    }

    private fun assertEquals(user1: User, user2: User) {
        assertEquals(user1.username, user2.username)
        assertEquals(user1.id, user2.id)
        assertEquals(user1.ipAddress, user2.ipAddress)
        assertEquals(user1.email, user2.email)
        assertEquals(user1.others, user2.others)
    }
}

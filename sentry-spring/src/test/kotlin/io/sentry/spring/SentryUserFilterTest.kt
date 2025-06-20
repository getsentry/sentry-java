package io.sentry.spring

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.protocol.User
import javax.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SentryUserFilterTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val chain = mock<FilterChain>()

    fun getSut(
      isSendDefaultPii: Boolean = false,
      userProviders: List<SentryUserProvider>,
    ): SentryUserFilter {
      val options = SentryOptions().apply { this.isSendDefaultPii = isSendDefaultPii }
      whenever(scopes.options).thenReturn(options)
      return SentryUserFilter(scopes, userProviders)
    }
  }

  private val fixture = Fixture()

  private val sampleUser =
    User().apply {
      username = "john.doe"
      id = "user-id"
      ipAddress = "192.168.0.1"
      email = "john.doe@example.com"
      data = mapOf("key" to "value")
    }

  @Test
  fun `sets provided user data on the scope`() {
    val filter = fixture.getSut(userProviders = listOf(SentryUserProvider { sampleUser }))

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes).setUser(check { assertEquals(sampleUser, it) })
  }

  @Test
  fun `when processor returns empty User, user data is not changed`() {
    val filter =
      fixture.getSut(
        userProviders = listOf(SentryUserProvider { sampleUser }, SentryUserProvider { User() })
      )

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes).setUser(check { assertEquals(sampleUser, it) })
  }

  @Test
  fun `when processor returns null, user data is not changed`() {
    val filter =
      fixture.getSut(
        userProviders = listOf(SentryUserProvider { sampleUser }, SentryUserProvider { null })
      )

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes).setUser(check { assertEquals(sampleUser, it) })
  }

  @Test
  fun `merges user#data with existing user#data set on SentryEvent`() {
    val filter =
      fixture.getSut(
        userProviders =
          listOf(
            SentryUserProvider { User().apply { data = mapOf("key" to "value") } },
            SentryUserProvider { User().apply { data = mapOf("new-key" to "new-value") } },
          )
      )

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes)
      .setUser(check { assertEquals(mapOf("key" to "value", "new-key" to "new-value"), it.data) })
  }

  @Test
  fun `when isSendDefaultPii is true and user is set with custom ip address, user ip is unchanged`() {
    val filter =
      fixture.getSut(
        isSendDefaultPii = true,
        userProviders = listOf(SentryUserProvider { User().apply { ipAddress = "192.168.0.1" } }),
      )

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes).setUser(check { assertEquals("192.168.0.1", it.ipAddress) })
  }

  @Test
  fun `when isSendDefaultPii is true and user is set with {{auto}} ip address, user ip is set to null`() {
    val filter =
      fixture.getSut(
        isSendDefaultPii = true,
        userProviders = listOf(SentryUserProvider { User().apply { ipAddress = "{{auto}}" } }),
      )

    filter.doFilter(fixture.request, fixture.response, fixture.chain)

    verify(fixture.scopes).setUser(check { assertNull(it.ipAddress) })
  }

  private fun assertEquals(user1: User, user2: User) {
    assertEquals(user1.username, user2.username)
    assertEquals(user1.id, user2.id)
    assertEquals(user1.ipAddress, user2.ipAddress)
    assertEquals(user1.email, user2.email)
    assertEquals(user1.data, user2.data)
  }
}

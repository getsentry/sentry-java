package io.sentry.spring

import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

class SpringSecuritySentryUserProviderTest {
  class Fixture {
    fun getSut(
      username: String? = null,
      userInfo: Boolean? = null,
    ): SpringSecuritySentryUserProvider {
      val options = SentryOptions().apply { userInfo?.let { dataCollection.setUserInfo(it) } }
      val securityContext = mock<SecurityContext>()
      if (username != null) {
        val authentication = mock<Authentication>()
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.name).thenReturn("name")
      } else {
        whenever(securityContext.authentication).thenReturn(null)
      }
      SecurityContextHolder.setContext(securityContext)
      return SpringSecuritySentryUserProvider(options)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `returns user with username set by default`() {
    val provider = fixture.getSut(username = "name")
    val user = provider.provideUser()
    assertNotNull(user) { assertEquals("name", it.username) }
  }

  @Test
  fun `when user info is disabled, returns null`() {
    val provider = fixture.getSut(username = "name", userInfo = false)

    assertNull(provider.provideUser())
  }

  @Test
  fun `when user info is enabled, returns user`() {
    val provider = fixture.getSut(username = "name", userInfo = true)

    assertNotNull(provider.provideUser()) { assertEquals("name", it.username) }
  }

  @Test
  fun `when security context is not set, returns null`() {
    val provider = fixture.getSut()
    val user = provider.provideUser()
    assertNull(user)
  }
}

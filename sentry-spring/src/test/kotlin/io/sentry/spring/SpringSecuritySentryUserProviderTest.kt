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
      isSendDefaultPii: Boolean = true,
      username: String? = null,
    ): SpringSecuritySentryUserProvider {
      val options = SentryOptions().apply { this.isSendDefaultPii = isSendDefaultPii }
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
  fun `when send default pii is set to true, returns user with username set`() {
    val provider = fixture.getSut(true, "name")
    val user = provider.provideUser()
    assertNotNull(user) { assertEquals("name", it.username) }
  }

  @Test
  fun `when send default pii is set to false, returns null`() {
    val provider = fixture.getSut(false)
    val user = provider.provideUser()
    assertNull(user)
  }

  @Test
  fun `when send default pii is set to true and security context is not set, returns null`() {
    val provider = fixture.getSut(true)
    val user = provider.provideUser()
    assertNull(user)
  }
}

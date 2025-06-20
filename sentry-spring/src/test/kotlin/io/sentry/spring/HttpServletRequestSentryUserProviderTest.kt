package io.sentry.spring

import io.sentry.SentryOptions
import java.security.Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class HttpServletRequestSentryUserProviderTest {
  @Test
  fun `attaches user's IP address to Sentry Event`() {
    val request = MockHttpServletRequest()
    request.addHeader("X-FORWARDED-FOR", "192.168.0.1,192.168.0.2")
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = SentryOptions()
    options.isSendDefaultPii = true
    val userProvider = HttpServletRequestSentryUserProvider(options)
    val result = userProvider.provideUser()

    assertNotNull(result)
    assertEquals("192.168.0.1", result.ipAddress)
  }

  @Test
  fun `attaches username to Sentry Event`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = SentryOptions()
    options.isSendDefaultPii = true
    val userProvider = HttpServletRequestSentryUserProvider(options)
    val result = userProvider.provideUser()

    assertNotNull(result)
    assertEquals("janesmith", result.username)
  }

  @Test
  fun `when sendDefaultPii is set to false, does not attach user data Sentry Event`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = SentryOptions()
    options.isSendDefaultPii = false
    val userProvider = HttpServletRequestSentryUserProvider(options)
    val result = userProvider.provideUser()

    assertNull(result)
  }
}

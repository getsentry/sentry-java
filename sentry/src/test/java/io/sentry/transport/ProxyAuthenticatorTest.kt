package io.sentry.transport

import java.net.Authenticator
import java.net.Authenticator.RequestorType
import java.net.PasswordAuthentication
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyAuthenticatorTest {
  @BeforeTest
  @AfterTest
  fun reset() {
    Authenticator.setDefault(null)
  }

  @Test
  fun `returns authentication when proxy request host matches proxy host`() {
    Authenticator.setDefault(ProxyAuthenticator("some-user", "some-password", "proxy.example.com"))

    val authentication = requestPasswordAuthentication("proxy.example.com", RequestorType.PROXY)

    assertEquals("some-user", authentication!!.userName)
    assertEquals("some-password", String(authentication.password))
  }

  @Test
  fun `returns null when requestor type is not proxy`() {
    Authenticator.setDefault(ProxyAuthenticator("some-user", "some-password", "proxy.example.com"))

    val authentication = requestPasswordAuthentication("proxy.example.com", RequestorType.SERVER)

    assertNull(authentication)
  }

  @Test
  fun `returns null when proxy request host does not match proxy host`() {
    Authenticator.setDefault(ProxyAuthenticator("some-user", "some-password", "proxy.example.com"))

    val authentication = requestPasswordAuthentication("other.example.com", RequestorType.PROXY)

    assertNull(authentication)
  }

  private fun requestPasswordAuthentication(
    host: String,
    requestorType: RequestorType,
  ): PasswordAuthentication? =
    Authenticator.requestPasswordAuthentication(
      host,
      null,
      8080,
      "https",
      "prompt",
      "basic",
      URL("https://sentry.io"),
      requestorType,
    )
}

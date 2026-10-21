package io.sentry.transport

import java.net.HttpURLConnection
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DistrustedCertificatesTest {

  private val connection = mock<HttpsURLConnection>()

  @Test
  fun `throws and disconnects when the chain contains a distrusted certificate`() {
    val certificates = arrayOf(trustedCertificate(), distrustedCertificate())
    whenever(connection.serverCertificates).thenReturn(certificates)

    assertFailsWith<SSLPeerUnverifiedException> { DistrustedCertificates.verify(connection) }

    verify(connection).disconnect()
  }

  @Test
  fun `accepts a chain without distrusted certificates`() {
    val certificates = arrayOf(trustedCertificate())
    whenever(connection.serverCertificates).thenReturn(certificates)

    DistrustedCertificates.verify(connection)

    verify(connection, never()).disconnect()
  }

  @Test
  fun `accepts a connection without server certificates`() {
    whenever(connection.serverCertificates).thenReturn(null)

    DistrustedCertificates.verify(connection)

    verify(connection, never()).disconnect()
  }

  @Test
  fun `ignores plain HTTP connections`() {
    val connection = mock<HttpURLConnection>()

    DistrustedCertificates.verify(connection)

    verify(connection, never()).disconnect()
  }

  companion object {
    /** EM DV TLS CA - G3B as cross-signed by Sectigo's AAA Certificate Services root. */
    fun distrustedCertificate(): Certificate =
      DistrustedCertificatesTest::class
        .java
        .classLoader!!
        .getResourceAsStream("emudhra-em-dv-tls-ca-g3b.pem")!!
        .use { CertificateFactory.getInstance("X.509").generateCertificate(it) }

    private fun trustedCertificate(): Certificate {
      val publicKey = mock<PublicKey>()
      whenever(publicKey.encoded).thenReturn(byteArrayOf(1, 2, 3))
      val certificate = mock<Certificate>()
      whenever(certificate.publicKey).thenReturn(publicKey)
      return certificate
    }
  }
}

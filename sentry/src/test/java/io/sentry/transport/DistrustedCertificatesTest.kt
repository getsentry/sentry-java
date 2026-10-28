package io.sentry.transport

import com.google.common.truth.Truth.assertThat
import io.sentry.JsonSerializer
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Rule

class DistrustedCertificatesTest {

  @get:Rule val server = MockWebServer()

  private val emSignRoot = certificate("emsign-root-tls-ca-g3.pem")

  /**
   * EM OV TLS CA - G3C, issued by emSign Root TLS CA - G3. Its private key is 4, as OkHttp found:
   * https://github.com/square/okhttp/blob/master/okhttp/src/commonTest/kotlin/okhttp3/CompromisedIntermediateTest.kt
   */
  private val distrustedCa =
    certificate("emudhra-em-ov-tls-ca-g3c.pem").let {
      HeldCertificate(KeyPair(it.publicKey, secp384r1PrivateKey(4)), it)
    }

  private val trustedCa = HeldCertificate.Builder().certificateAuthority(0).build()

  private val options = SentryOptions().apply { setSerializer(JsonSerializer(this)) }

  @Test
  fun `rejects a certificate issued by a distrusted CA`() {
    assertFailsWith<SSLPeerUnverifiedException> {
      DistrustedCertificates.check(arrayOf(leafIssuedBy(distrustedCa).certificate))
    }
  }

  @Test
  fun `rejects a distrusted CA cross-signed by AAA Certificate Services`() {
    assertFailsWith<SSLPeerUnverifiedException> {
      DistrustedCertificates.check(arrayOf(certificate("emudhra-em-dv-tls-ca-g3b.pem")))
    }
  }

  @Test
  fun `accepts a chain without distrusted CAs`() {
    DistrustedCertificates.check(
      arrayOf(leafIssuedBy(trustedCa).certificate, trustedCa.certificate)
    )
  }

  @Test
  fun `sends the envelope to a server whose certificate a trusted CA issued`() {
    server.useHttps(serverCertificates(leafIssuedBy(trustedCa)), false)
    server.enqueue(MockResponse())

    val result = send(trusting(emSignRoot, trustedCa.certificate))

    assertThat(result.isSuccess).isTrue()
    assertThat(server.requestCount).isEqualTo(1)
  }

  @Test
  fun `still verifies the hostname`() {
    server.useHttps(serverCertificates(leafIssuedBy(trustedCa, "sentry.example")), false)

    assertFailsWith<IOException> { send(trusting(trustedCa.certificate)) }

    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `does not send the envelope to a server whose certificate a distrusted CA issued`() {
    server.useHttps(
      serverCertificates(leafIssuedBy(distrustedCa), distrustedCa.certificate),
      false,
    )

    assertFailsWith<SSLPeerUnverifiedException> { send(trusting(emSignRoot)) }

    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `does not send the envelope when the client completes the chain with a distrusted CA the server left out`() {
    // Like Android, which completes the chain from intermediates it cached on earlier connections.
    server.useHttps(serverCertificates(leafIssuedBy(distrustedCa)), false)

    assertFailsWith<SSLPeerUnverifiedException> { send(trusting(distrustedCa.certificate)) }

    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `does not send the envelope again when HttpURLConnection retries it on a connection to a distrusted CA`() {
    server.useHttps(
      PerConnectionSocketFactory(
        serverCertificates(leafIssuedBy(trustedCa)),
        serverCertificates(leafIssuedBy(distrustedCa), distrustedCa.certificate),
      ),
      false,
    )
    // The JVM sends a POST again on a new connection when the first one closes before a response.
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
    server.enqueue(MockResponse())

    val result = send(trusting(emSignRoot, trustedCa.certificate))

    assertThat(result.isSuccess).isFalse()
    assertThat(server.requestCount).isEqualTo(1)
  }

  private fun send(sslSocketFactory: SSLSocketFactory): TransportResult {
    options.sslSocketFactory = sslSocketFactory
    val requestDetails = RequestDetails(server.url("/api/1/envelope/").toString(), emptyMap())
    val connection = HttpConnection(options, requestDetails, RateLimiter(options))
    return connection.send(SentryEnvelope.from(options.serializer, SentryEvent(), null))
  }

  private fun leafIssuedBy(
    issuer: HeldCertificate,
    hostname: String = server.hostName,
  ): HeldCertificate =
    HeldCertificate.Builder().addSubjectAlternativeName(hostname).signedBy(issuer).build()

  private fun serverCertificates(
    leaf: HeldCertificate,
    vararg intermediates: X509Certificate,
  ): SSLSocketFactory =
    HandshakeCertificates.Builder().heldCertificate(leaf, *intermediates).build().sslSocketFactory()

  private fun trusting(vararg certificates: X509Certificate): SSLSocketFactory =
    HandshakeCertificates.Builder()
      .apply { certificates.forEach { addTrustedCertificate(it) } }
      .build()
      .sslSocketFactory()

  private fun certificate(resource: String): X509Certificate =
    javaClass.classLoader!!.getResourceAsStream(resource)!!.use {
      CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

  private fun secp384r1PrivateKey(value: Long): PrivateKey {
    val secp384r1 =
      AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp384r1"))
        getParameterSpec(ECParameterSpec::class.java)
      }
    return KeyFactory.getInstance("EC")
      .generatePrivate(ECPrivateKeySpec(BigInteger.valueOf(value), secp384r1))
  }

  /** Presents the certificates of [first] on the first connection and of [then] on later ones. */
  private class PerConnectionSocketFactory(
    private val first: SSLSocketFactory,
    private val then: SSLSocketFactory,
  ) : SSLSocketFactory() {
    private var connections = 0

    private fun next(): SSLSocketFactory = if (connections++ == 0) first else then

    override fun createSocket(socket: Socket, host: String?, port: Int, autoClose: Boolean) =
      next().createSocket(socket, host, port, autoClose)

    override fun getDefaultCipherSuites(): Array<String> = first.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = first.supportedCipherSuites

    override fun createSocket(host: String, port: Int) = throw UnsupportedOperationException()

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) =
      throw UnsupportedOperationException()

    override fun createSocket(host: InetAddress, port: Int) = throw UnsupportedOperationException()

    override fun createSocket(
      address: InetAddress,
      port: Int,
      localAddress: InetAddress,
      localPort: Int,
    ) = throw UnsupportedOperationException()
  }
}

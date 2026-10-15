package io.sentry.android.core

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SentryRootCaSslSocketFactoryTest {

  @Test
  fun `bundles ISRG Root X1 matching the fingerprint published on docs_sentry_io`() {
    val keyStore = SentryRootCaSslSocketFactory.createTrustStore(emptyArray())

    val fingerprints =
      keyStore.aliases().toList().map { alias -> keyStore.getCertificate(alias).sha256() }

    assertThat(fingerprints)
      .containsExactly(
        // ISRG Root X1
        ISRG_ROOT_X1_SHA256
      )
  }

  @Test
  fun `trust store keeps the system trusted CAs and adds the bundled root`() {
    // The JVM's cacerts already has the ISRG roots, so remove them to act like an old Android
    // device.
    val systemIssuers = systemIssuersWithoutIsrgRoots()
    assertThat(systemIssuers).isNotEmpty()

    val trustManager =
      defaultTrustManager(SentryRootCaSslSocketFactory.createTrustStore(systemIssuers))

    val accepted = trustManager.acceptedIssuers.map { it.sha256() }
    assertThat(accepted).containsAtLeastElementsIn(systemIssuers.map { it.sha256() })
    assertThat(accepted).contains(ISRG_ROOT_X1_SHA256)
  }

  @Test
  fun `Let's Encrypt chain only validates with the bundled root`() {
    val chain = letsEncryptChain()

    // Without any ISRG root, like Android 7.1.0 and older.
    val ex =
      assertFailsWith<CertPathValidatorException> {
        validate(chain, plainKeyStore(systemIssuersWithoutIsrgRoots()))
      }
    assertThat(ex.message).contains("trust anchor")

    // The same system CAs plus the bundled root.
    validate(chain, SentryRootCaSslSocketFactory.createTrustStore(systemIssuersWithoutIsrgRoots()))
    // Only the bundled root.
    validate(chain, SentryRootCaSslSocketFactory.createTrustStore(emptyArray()))
  }

  @Test
  fun `falls back to the default SSLSocketFactory once when building the delegate throws a RuntimeException`() {
    val logger = mock<ILogger>()
    val calls = AtomicInteger()
    val sut =
      SentryRootCaSslSocketFactory(logger) {
        calls.incrementAndGet()
        throw IllegalStateException("broken OEM trust store")
      }

    assertThat(sut.delegate).isSameInstanceAs(HttpsURLConnection.getDefaultSSLSocketFactory())
    assertThat(sut.supportedCipherSuites).isNotEmpty()
    assertThat(sut.delegate).isSameInstanceAs(HttpsURLConnection.getDefaultSSLSocketFactory())

    assertThat(calls.get()).isEqualTo(1)
    verify(logger).log(eq(SentryLevel.ERROR), any<String>(), any<IllegalStateException>())
  }

  @Test
  fun `creates a custom delegate SSLSocketFactory lazily and only once`() {
    val sut = SentryRootCaSslSocketFactory(mock<ILogger>())

    val delegate = sut.delegate

    assertThat(delegate).isNotSameInstanceAs(HttpsURLConnection.getDefaultSSLSocketFactory())
    assertThat(sut.supportedCipherSuites).isNotEmpty()
    assertThat(sut.delegate).isSameInstanceAs(delegate)
  }

  @Test
  fun `isSentrySaasDsn matches sentry_io and its subdomains only`() {
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@o1.ingest.us.sentry.io/1"))
      .isTrue()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@o1.ingest.sentry.io/1"))
      .isTrue()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key:secret@sentry.io:443/1"))
      .isTrue()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@O1.Ingest.SENTRY.IO./1"))
      .isTrue()

    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn(null)).isFalse()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("")).isFalse()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@sentry.example.com/1"))
      .isFalse()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@notsentry.io/1")).isFalse()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@sentry.io.evil.com/1"))
      .isFalse()
    assertThat(SentryRootCaSslSocketFactory.isSentrySaasDsn("https://key@evil.com/sentry.io"))
      .isFalse()
  }

  @Test
  fun `does not swallow Errors when building the delegate`() {
    val sut =
      SentryRootCaSslSocketFactory(mock<ILogger>()) { throw OutOfMemoryError("low memory device") }

    assertFailsWith<OutOfMemoryError> { sut.delegate }
  }

  /** The JVM's system CAs without ISRG Root X1 and any other ISRG root, such as Root YR. */
  private fun systemIssuersWithoutIsrgRoots(): Array<X509Certificate> =
    defaultTrustManager(null)
      .acceptedIssuers
      .filter {
        val subject = it.subjectX500Principal.name
        it.sha256() != ISRG_ROOT_X1_SHA256 &&
          !subject.contains("O=ISRG") &&
          !subject.contains("O=Internet Security Research Group")
      }
      .toTypedArray()

  private fun plainKeyStore(certificates: Array<X509Certificate>): KeyStore =
    KeyStore.getInstance(KeyStore.getDefaultType()).apply {
      load(null, null)
      certificates.forEachIndexed { i, certificate -> setCertificateEntry("ca-$i", certificate) }
    }

  /**
   * A real chain served by valid-isrgrootx1.letsencrypt.org: leaf, YR1 intermediate and Root YR
   * cross-signed by ISRG Root X1.
   */
  private fun letsEncryptChain(): List<X509Certificate> {
    val factory = CertificateFactory.getInstance("X.509")
    return listOf(
        "letsencrypt-leaf.pem",
        "letsencrypt-yr1.pem",
        "letsencrypt-root-yr-cross-signed-by-isrg-root-x1.pem",
      )
      .map { name ->
        javaClass.classLoader!!.getResourceAsStream("root-ca/$name").use {
          factory.generateCertificate(it) as X509Certificate
        }
      }
  }

  /**
   * Validates the chain with PKIX. Validates at a fixed date within the leaf's validity, so the
   * test doesn't break when the leaf certificate expires.
   */
  private fun validate(chain: List<X509Certificate>, trustStore: KeyStore) {
    val params =
      PKIXParameters(trustStore).apply {
        isRevocationEnabled = false
        date = Date(VALIDATION_DATE_MILLIS)
      }
    CertPathValidator.getInstance("PKIX")
      .validate(CertificateFactory.getInstance("X.509").generateCertPath(chain), params)
  }

  private fun defaultTrustManager(keyStore: KeyStore?): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(keyStore)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
  }

  private fun Certificate.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

  companion object {
    private const val ISRG_ROOT_X1_SHA256 =
      "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6"

    // 2026-10-14T00:00:00Z, within the validity of all certificates in the Let's Encrypt chain.
    private const val VALIDATION_DATE_MILLIS = 1791936000000L
  }
}

package io.sentry.android.core

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import org.mockito.kotlin.mock

class SentryRootCaSslSocketFactoryTest {

  @Test
  fun `bundles ISRG Root X1 matching the fingerprint published on docs_sentry_io`() {
    val keyStore = SentryRootCaSslSocketFactory.createTrustStore(emptyArray())

    val fingerprints =
      keyStore.aliases().toList().map { alias -> keyStore.getCertificate(alias).sha256() }

    assertThat(fingerprints)
      .containsExactly(
        // ISRG Root X1
        "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6"
      )
  }

  @Test
  fun `trust store keeps the system trusted CAs`() {
    val systemIssuers = defaultTrustManager(null).acceptedIssuers
    assertThat(systemIssuers).isNotEmpty()

    val trustManager =
      defaultTrustManager(SentryRootCaSslSocketFactory.createTrustStore(systemIssuers))

    val accepted = trustManager.acceptedIssuers.map { it.sha256() }
    assertThat(accepted).containsAtLeastElementsIn(systemIssuers.map { it.sha256() })
    assertThat(accepted)
      .containsAtLeastElementsIn(
        SentryRootCaSslSocketFactory.createTrustStore(emptyArray()).let { store ->
          store.aliases().toList().map { store.getCertificate(it).sha256() }
        }
      )
  }

  @Test
  fun `creates a custom delegate SSLSocketFactory lazily and only once`() {
    val sut = SentryRootCaSslSocketFactory(mock<ILogger>())

    val delegate = sut.delegate

    assertThat(delegate).isNotSameInstanceAs(HttpsURLConnection.getDefaultSSLSocketFactory())
    assertThat(sut.supportedCipherSuites).isNotEmpty()
    assertThat(sut.delegate).isSameInstanceAs(delegate)
  }

  private fun defaultTrustManager(keyStore: KeyStore?): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(keyStore)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
  }

  private fun Certificate.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }
}

package io.sentry.android.core

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import java.security.KeyStore
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import org.mockito.kotlin.mock

class SentryRootCaSslSocketFactoryTest {

  @Test
  fun `bundled root CAs match the fingerprints published on docs_sentry_io`() {
    val keyStore = SentryRootCaSslSocketFactory.createTrustStore(emptyArray())

    val fingerprints =
      keyStore.aliases().toList().map { alias -> keyStore.getCertificate(alias).sha256() }

    assertThat(fingerprints)
      .containsExactly(
        // DigiCert Global Root CA
        "43:48:A0:E9:44:4C:78:CB:26:5E:05:8D:5E:89:44:B4:D8:4F:96:62:BD:26:DB:25:7F:89:34:A4:43:C7:01:61",
        // DigiCert Global Root G2
        "CB:3C:CB:B7:60:31:E5:E0:13:8F:8D:D3:9A:23:F9:DE:47:FF:C3:5E:43:C1:14:4C:EA:27:D4:6A:5A:B1:CB:5F",
        // ISRG Root X1
        "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6",
        // ISRG Root X2
        "69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70",
        // GTS Root R1
        "D9:47:43:2A:BD:E7:B7:FA:90:FC:2E:6B:59:10:1B:12:80:E0:E1:C7:E4:E4:0F:A3:C6:88:7F:FF:57:A7:F4:CF",
        // GTS Root R2
        "8D:25:CD:97:22:9D:BF:70:35:6B:DA:4E:B3:CC:73:40:31:E2:4C:F0:0F:AF:CF:D3:2D:C7:6E:B5:84:1C:7E:A8",
        // GTS Root R3
        "34:D8:A7:3E:E2:08:D9:BC:DB:0D:95:65:20:93:4B:4E:40:E6:94:82:59:6E:8B:6F:73:C8:42:6B:01:0A:6F:48",
        // GTS Root R4
        "34:9D:FA:40:58:C5:E2:63:12:3B:39:8A:E7:95:57:3C:4E:13:13:C8:3F:E6:8F:93:55:6C:D5:E8:03:1B:3C:7D",
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

  private fun java.security.cert.Certificate.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }
}

package io.sentry.transport;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Rejects servers whose certificate was issued by a CA that is known to be compromised but still
 * trusted by platform trust stores.
 *
 * <p>Browsers block such certificates on their own and require Certificate Transparency, but {@link
 * javax.net.ssl.HttpsURLConnection} does neither. Android only updates its CA store with OS updates
 * (or Mainline on Android 14+) and the JVM only with JDK updates, so a distrust by the root
 * programs doesn't reach every device.
 */
@ApiStatus.Internal
public final class DistrustedCertificates {

  /**
   * The eight eMudhra intermediates whose private keys can be recovered from their certificates
   * (CVE-2026-111943). Each key is in two certificates with the same subject: one issued by eMudhra
   * and a cross-sign by Sectigo's AAA Certificate Services root, which is trusted on every Android
   * version.
   *
   * <p>They are matched by name rather than by key because the server doesn't have to send them:
   * Android caches intermediates from earlier connections and completes a chain that is missing
   * one. The server always sends the leaf, which names its issuer, and since none of these CAs may
   * issue further CAs ({@code pathlen:0}), any certificate they issued is a leaf.
   *
   * @see <a
   *     href="https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE">Compromised
   *     eMudhra Intermediate Certificates</a>
   */
  private static final Set<X500Principal> DISTRUSTED_CAS =
      new HashSet<>(
          Arrays.asList(
              new X500Principal("CN=EM DV TLS CA - G3B, O=eMudhra BV, C=NL"),
              new X500Principal("CN=EM EV TLS CA - G3B, O=eMudhra BV, C=NL"),
              new X500Principal("CN=EM OV TLS CA - G3B, O=eMudhra BV, C=NL"),
              new X500Principal("CN=EM OV CS CA - G3B, O=eMudhra BV, C=NL"),
              new X500Principal("CN=EM DV TLS CA - G3C, O=eMudhra Inc, C=US"),
              new X500Principal("CN=EM EV TLS CA - G3C, O=eMudhra Inc, C=US"),
              new X500Principal("CN=EM OV TLS CA - G3C, O=eMudhra Inc, C=US"),
              new X500Principal("CN=EM OV CS CA - G3C, O=eMudhra Inc, C=US")));

  private DistrustedCertificates() {}

  /**
   * Wraps {@code delegate} so that every TLS handshake of its sockets is checked, including the
   * ones {@link java.net.HttpURLConnection} makes on its own to retry a request on a new
   * connection.
   */
  public static @NotNull SSLSocketFactory wrap(final @NotNull SSLSocketFactory delegate) {
    return new DistrustedCertificatesSocketFactory(delegate);
  }

  /**
   * @param peerCertificates the certificates the server sent, leaf first
   */
  static void check(final @NotNull Certificate[] peerCertificates)
      throws SSLPeerUnverifiedException {
    for (final Certificate certificate : peerCertificates) {
      if (certificate instanceof X509Certificate && isDistrusted((X509Certificate) certificate)) {
        throw new SSLPeerUnverifiedException(
            "The server's certificate chain contains a distrusted certificate.");
      }
    }
  }

  private static boolean isDistrusted(final @NotNull X509Certificate certificate) {
    return DISTRUSTED_CAS.contains(certificate.getIssuerX500Principal())
        || DISTRUSTED_CAS.contains(certificate.getSubjectX500Principal());
  }
}

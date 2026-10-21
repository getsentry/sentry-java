package io.sentry.transport;

import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rejects servers whose certificate chain contains a CA certificate that is known to be compromised
 * but still trusted by platform trust stores.
 *
 * <p>Browsers block such certificates on their own and require Certificate Transparency, but {@link
 * HttpsURLConnection} does neither. Android only updates its CA store with OS updates (or Mainline
 * on Android 14+) and the JVM only with JDK updates, so a distrust by the root programs doesn't
 * reach every device.
 */
@ApiStatus.Internal
public final class DistrustedCertificates {

  /**
   * SHA-256 hashes of the SubjectPublicKeyInfo of the eight eMudhra intermediates whose private
   * keys can be recovered from their certificates (CVE-2026-111943). Each key is in two
   * certificates: one issued by eMudhra and a cross-sign by Sectigo's AAA Certificate Services
   * root, which is trusted on every Android version. Matching the key covers both.
   *
   * @see <a
   *     href="https://groups.google.com/a/mozilla.org/g/dev-security-policy/c/0R3XLJL6FuE">Compromised
   *     eMudhra Intermediate Certificates</a>
   */
  private static final Set<String> SPKI_SHA256_HASHES =
      new HashSet<>(
          Arrays.asList(
              "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
              "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
              "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
              "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
              "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
              "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
              "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
              "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6"));

  private DistrustedCertificates() {}

  /**
   * Disconnects and throws if the server presented a distrusted certificate.
   *
   * <p>Call it right after {@link HttpURLConnection#connect()}: the TLS handshake is done by then,
   * but no part of the request has been written yet. A {@link javax.net.ssl.HostnameVerifier} can't
   * do this check, because the JVM's {@link HttpsURLConnection} only calls it when its own hostname
   * check fails.
   */
  public static void verify(final @NotNull HttpURLConnection connection)
      throws SSLPeerUnverifiedException {
    if (!(connection instanceof HttpsURLConnection)) {
      return;
    }
    final @Nullable Certificate[] certificates =
        ((HttpsURLConnection) connection).getServerCertificates();
    if (certificates == null) {
      return;
    }
    for (final @NotNull Certificate certificate : certificates) {
      if (SPKI_SHA256_HASHES.contains(spkiSha256(certificate))) {
        connection.disconnect();
        throw new SSLPeerUnverifiedException(
            "The server's certificate chain contains a distrusted certificate.");
      }
    }
  }

  private static @NotNull String spkiSha256(final @NotNull Certificate certificate) {
    final byte[] hash;
    try {
      hash = MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded());
    } catch (NoSuchAlgorithmException e) {
      // Every Java and Android platform is required to support SHA-256.
      throw new AssertionError(e);
    }
    final StringBuilder hex = new StringBuilder(hash.length * 2);
    for (final byte b : hash) {
      hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
    }
    return hex.toString();
  }
}

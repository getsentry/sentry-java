package io.sentry.android.core;

import io.sentry.DsnUtil;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import io.sentry.util.LazyEvaluator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * An {@link SSLSocketFactory} that trusts the system root CAs plus the root CAs bundled in {@link
 * SentryRootCertificates}. Both are put into a single trust store which is handed to the platform's
 * default {@link TrustManagerFactory}, so certificate validation itself is still done by the
 * platform.
 *
 * <p>Android API 25 and lower may not ship ISRG Root X1, so TLS handshakes with Let's Encrypt
 * certificates fail on those devices. This factory is only set as {@link
 * io.sentry.SentryOptions#setSslSocketFactory(SSLSocketFactory)} on those API levels and only when
 * the DSN points to Sentry SaaS ({@code sentry.io}), see {@link #isSentrySaasDsn(String)}. So it
 * only applies to the SDK's own envelope uploads to Sentry and not to any other connection of the
 * app.
 *
 * <p>On API 24 and 25, the system CAs are read from the Network Security Config's base config. Any
 * {@code domain-config} trust anchors or pins do not apply to connections using this factory. This
 * is why it's limited to Sentry SaaS: self-hosted Sentry with a private CA keeps using the platform
 * default.
 *
 * <p>The underlying {@link SSLContext} is created lazily on first use, so the cost of parsing the
 * certificates is paid on the transport thread instead of during {@code SentryAndroid.init}. If
 * creating it fails for any reason, the platform default {@link SSLSocketFactory} is used and the
 * failure is not retried.
 */
@ApiStatus.Internal
final class SentryRootCaSslSocketFactory extends SSLSocketFactory {

  static final @NotNull String SENTRY_ROOT_CA_ALIAS_PREFIX = "sentry-root-ca-";

  private final @NotNull LazyEvaluator<SSLSocketFactory> delegate;

  /** Provides the CAs trusted by the platform. */
  interface SystemTrustedIssuersProvider {
    @NotNull
    X509Certificate[] getSystemTrustedIssuers() throws GeneralSecurityException;
  }

  SentryRootCaSslSocketFactory(final @NotNull ILogger logger) {
    this(logger, SentryRootCaSslSocketFactory::getSystemTrustedIssuers);
  }

  @VisibleForTesting
  SentryRootCaSslSocketFactory(
      final @NotNull ILogger logger,
      final @NotNull SystemTrustedIssuersProvider systemTrustedIssuersProvider) {
    this.delegate = new LazyEvaluator<>(() -> createDelegate(logger, systemTrustedIssuersProvider));
  }

  /**
   * Returns {@code true} if the DSN's host is {@code sentry.io} or a subdomain of it, for example
   * {@code o123.ingest.us.sentry.io}. Those are the hosts whose certificates chain to the bundled
   * roots. The host is read with {@link DsnUtil#getHost(String)}, which parses it the same way as
   * the DSN the SDK sends envelopes to.
   */
  static boolean isSentrySaasDsn(final @Nullable String dsn) {
    @Nullable String host = DsnUtil.getHost(dsn);
    if (host == null) {
      return false;
    }
    if (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    host = host.toLowerCase(Locale.ROOT);
    return host.equals("sentry.io") || host.endsWith(".sentry.io");
  }

  private static @NotNull SSLSocketFactory createDelegate(
      final @NotNull ILogger logger,
      final @NotNull SystemTrustedIssuersProvider systemTrustedIssuersProvider) {
    try {
      final @NotNull TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(
          createTrustStore(systemTrustedIssuersProvider.getSystemTrustedIssuers()));

      final @NotNull SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
      return sslContext.getSocketFactory();
    } catch (Exception e) {
      // Catches Exception, not only GeneralSecurityException and IOException: some OEM builds
      // throw RuntimeExceptions from their trust store or certificate code. An exception escaping
      // here would make every envelope upload fail without being retried, and the LazyEvaluator
      // would try to build the delegate again on every send. Errors such as OutOfMemoryError are
      // not caught on purpose, the process can't recover from them.
      logger.log(
          SentryLevel.ERROR,
          "Failed to create SSLSocketFactory with bundled Sentry root CAs, using the default one.",
          e);
      return HttpsURLConnection.getDefaultSSLSocketFactory();
    }
  }

  /** Returns the CAs trusted by the platform's default trust manager. */
  private static @NotNull X509Certificate[] getSystemTrustedIssuers()
      throws GeneralSecurityException {
    final @NotNull TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init((KeyStore) null);
    for (final TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
      if (trustManager instanceof X509TrustManager) {
        return ((X509TrustManager) trustManager).getAcceptedIssuers();
      }
    }
    throw new GeneralSecurityException("No default X509TrustManager available");
  }

  /**
   * Creates a trust store containing the given system trusted CAs plus the bundled Sentry root CAs.
   */
  @VisibleForTesting
  static @NotNull KeyStore createTrustStore(final @NotNull X509Certificate[] systemTrustedIssuers)
      throws GeneralSecurityException, IOException {
    final @NotNull KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    for (int i = 0; i < systemTrustedIssuers.length; i++) {
      keyStore.setCertificateEntry("system-ca-" + i, systemTrustedIssuers[i]);
    }
    final @NotNull CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    for (int i = 0; i < SentryRootCertificates.ALL.length; i++) {
      final @NotNull Certificate certificate =
          certificateFactory.generateCertificate(
              new ByteArrayInputStream(
                  SentryRootCertificates.ALL[i].getBytes(StandardCharsets.UTF_8)));
      keyStore.setCertificateEntry(SENTRY_ROOT_CA_ALIAS_PREFIX + i, certificate);
    }
    return keyStore;
  }

  @TestOnly
  @NotNull
  SSLSocketFactory getDelegate() {
    return delegate.getValue();
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getValue().getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getValue().getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket() throws IOException {
    return delegate.getValue().createSocket();
  }

  @Override
  public Socket createSocket(
      final Socket socket, final String host, final int port, final boolean autoClose)
      throws IOException {
    return delegate.getValue().createSocket(socket, host, port, autoClose);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    return delegate.getValue().createSocket(host, port);
  }

  @Override
  public Socket createSocket(
      final String host, final int port, final InetAddress localHost, final int localPort)
      throws IOException {
    return delegate.getValue().createSocket(host, port, localHost, localPort);
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    return delegate.getValue().createSocket(host, port);
  }

  @Override
  public Socket createSocket(
      final InetAddress address,
      final int port,
      final InetAddress localAddress,
      final int localPort)
      throws IOException {
    return delegate.getValue().createSocket(address, port, localAddress, localPort);
  }
}

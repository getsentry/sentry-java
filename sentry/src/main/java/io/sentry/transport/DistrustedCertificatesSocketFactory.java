package io.sentry.transport;

import io.sentry.util.Platform;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.jetbrains.annotations.NotNull;

/** Creates sockets that run {@link DistrustedCertificates#check} after every TLS handshake. */
final class DistrustedCertificatesSocketFactory extends SSLSocketFactory {

  /**
   * Conscrypt on Android 5 only sends SNI after {@code setHostname} has been called, which
   * Android's HTTP stack does by reflection on the socket it gets. That call silently does nothing
   * on our wrapper, so we make it on the delegate instead. Later versions send SNI by default, and
   * calling the method there would access a hidden API.
   */
  private static final boolean ENABLE_SNI_BY_REFLECTION =
      Platform.isAndroid() && androidSdkInt() < 23;

  private final @NotNull SSLSocketFactory delegate;

  DistrustedCertificatesSocketFactory(final @NotNull SSLSocketFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket() throws IOException {
    return wrap(delegate.createSocket());
  }

  @Override
  public Socket createSocket(
      final Socket socket, final String host, final int port, final boolean autoClose)
      throws IOException {
    final Socket sslSocket = delegate.createSocket(socket, host, port, autoClose);
    if (ENABLE_SNI_BY_REFLECTION) {
      setHostname(sslSocket, host);
    }
    return wrap(sslSocket);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    return wrap(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(
      final String host, final int port, final InetAddress localHost, final int localPort)
      throws IOException {
    return wrap(delegate.createSocket(host, port, localHost, localPort));
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    return wrap(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(
      final InetAddress address,
      final int port,
      final InetAddress localAddress,
      final int localPort)
      throws IOException {
    return wrap(delegate.createSocket(address, port, localAddress, localPort));
  }

  private static @NotNull Socket wrap(final @NotNull Socket socket) {
    return socket instanceof SSLSocket
        ? new DistrustedCertificatesSocket((SSLSocket) socket)
        : socket;
  }

  private static void setHostname(final @NotNull Socket socket, final @NotNull String host) {
    try {
      socket.getClass().getMethod("setHostname", String.class).invoke(socket, host);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      // Not a Conscrypt socket, which leaves SNI as the delegate configured it.
    }
  }

  private static int androidSdkInt() {
    try {
      return Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      return Integer.MAX_VALUE;
    }
  }
}

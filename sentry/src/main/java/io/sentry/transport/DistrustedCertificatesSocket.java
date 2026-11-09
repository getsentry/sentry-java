package io.sentry.transport;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates to an {@link SSLSocket} and runs {@link DistrustedCertificates#check} once its
 * handshake is done.
 *
 * <p>The HTTP stacks of the JVM and Android call {@link #startHandshake()} before they send
 * anything. The streams check again before every read and write, so that application data can't
 * flow over a handshake that was started implicitly. They can't check when they are created,
 * because the JVM gets the output stream before it configures the socket for the handshake.
 */
// The delegate synchronizes its own state, so the methods Socket synchronizes on the wrapper don't
// need to.
@SuppressWarnings("UnsynchronizedOverridesSynchronized")
final class DistrustedCertificatesSocket extends SSLSocket {

  private final @NotNull SSLSocket delegate;
  private volatile boolean verified;

  DistrustedCertificatesSocket(final @NotNull SSLSocket delegate) {
    this.delegate = delegate;
  }

  @Override
  public void startHandshake() throws IOException {
    delegate.startHandshake();
    verify();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new VerifyingInputStream(delegate.getInputStream());
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return new VerifyingOutputStream(delegate.getOutputStream());
  }

  private void verify() throws IOException {
    if (verified) {
      return;
    }
    try {
      DistrustedCertificates.check(delegate.getSession().getPeerCertificates());
    } catch (SSLPeerUnverifiedException e) {
      delegate.close();
      throw e;
    }
    verified = true;
  }

  private final class VerifyingInputStream extends FilterInputStream {
    VerifyingInputStream(final @NotNull InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      verify();
      return in.read();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
      verify();
      return in.read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
      verify();
      return in.skip(n);
    }
  }

  private final class VerifyingOutputStream extends FilterOutputStream {
    VerifyingOutputStream(final @NotNull OutputStream out) {
      super(out);
    }

    @Override
    public void write(final int b) throws IOException {
      verify();
      out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
      verify();
      out.write(b, off, len);
    }
  }

  // Everything below delegates unchanged.

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public String[] getEnabledCipherSuites() {
    return delegate.getEnabledCipherSuites();
  }

  @Override
  public void setEnabledCipherSuites(final String[] suites) {
    delegate.setEnabledCipherSuites(suites);
  }

  @Override
  public String[] getSupportedProtocols() {
    return delegate.getSupportedProtocols();
  }

  @Override
  public String[] getEnabledProtocols() {
    return delegate.getEnabledProtocols();
  }

  @Override
  public void setEnabledProtocols(final String[] protocols) {
    delegate.setEnabledProtocols(protocols);
  }

  @Override
  public SSLSession getSession() {
    return delegate.getSession();
  }

  @Override
  public void addHandshakeCompletedListener(final HandshakeCompletedListener listener) {
    delegate.addHandshakeCompletedListener(listener);
  }

  @Override
  public void removeHandshakeCompletedListener(final HandshakeCompletedListener listener) {
    delegate.removeHandshakeCompletedListener(listener);
  }

  @Override
  public void setUseClientMode(final boolean mode) {
    delegate.setUseClientMode(mode);
  }

  @Override
  public boolean getUseClientMode() {
    return delegate.getUseClientMode();
  }

  @Override
  public void setNeedClientAuth(final boolean need) {
    delegate.setNeedClientAuth(need);
  }

  @Override
  public boolean getNeedClientAuth() {
    return delegate.getNeedClientAuth();
  }

  @Override
  public void setWantClientAuth(final boolean want) {
    delegate.setWantClientAuth(want);
  }

  @Override
  public boolean getWantClientAuth() {
    return delegate.getWantClientAuth();
  }

  @Override
  public void setEnableSessionCreation(final boolean flag) {
    delegate.setEnableSessionCreation(flag);
  }

  @Override
  public boolean getEnableSessionCreation() {
    return delegate.getEnableSessionCreation();
  }

  @Override
  public SSLParameters getSSLParameters() {
    return delegate.getSSLParameters();
  }

  @Override
  public void setSSLParameters(final SSLParameters params) {
    delegate.setSSLParameters(params);
  }

  @Override
  public void connect(final SocketAddress endpoint) throws IOException {
    delegate.connect(endpoint);
  }

  @Override
  public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
    delegate.connect(endpoint, timeout);
  }

  @Override
  public void bind(final SocketAddress bindpoint) throws IOException {
    delegate.bind(bindpoint);
  }

  @Override
  public InetAddress getInetAddress() {
    return delegate.getInetAddress();
  }

  @Override
  public InetAddress getLocalAddress() {
    return delegate.getLocalAddress();
  }

  @Override
  public int getPort() {
    return delegate.getPort();
  }

  @Override
  public int getLocalPort() {
    return delegate.getLocalPort();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return delegate.getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return delegate.getLocalSocketAddress();
  }

  @Override
  public SocketChannel getChannel() {
    return delegate.getChannel();
  }

  @Override
  public void setTcpNoDelay(final boolean on) throws SocketException {
    delegate.setTcpNoDelay(on);
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return delegate.getTcpNoDelay();
  }

  @Override
  public void setSoLinger(final boolean on, final int linger) throws SocketException {
    delegate.setSoLinger(on, linger);
  }

  @Override
  public int getSoLinger() throws SocketException {
    return delegate.getSoLinger();
  }

  @Override
  public void sendUrgentData(final int data) throws IOException {
    delegate.sendUrgentData(data);
  }

  @Override
  public void setOOBInline(final boolean on) throws SocketException {
    delegate.setOOBInline(on);
  }

  @Override
  public boolean getOOBInline() throws SocketException {
    return delegate.getOOBInline();
  }

  @Override
  public void setSoTimeout(final int timeout) throws SocketException {
    delegate.setSoTimeout(timeout);
  }

  @Override
  public int getSoTimeout() throws SocketException {
    return delegate.getSoTimeout();
  }

  @Override
  public void setSendBufferSize(final int size) throws SocketException {
    delegate.setSendBufferSize(size);
  }

  @Override
  public int getSendBufferSize() throws SocketException {
    return delegate.getSendBufferSize();
  }

  @Override
  public void setReceiveBufferSize(final int size) throws SocketException {
    delegate.setReceiveBufferSize(size);
  }

  @Override
  public int getReceiveBufferSize() throws SocketException {
    return delegate.getReceiveBufferSize();
  }

  @Override
  public void setKeepAlive(final boolean on) throws SocketException {
    delegate.setKeepAlive(on);
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return delegate.getKeepAlive();
  }

  @Override
  public void setTrafficClass(final int tc) throws SocketException {
    delegate.setTrafficClass(tc);
  }

  @Override
  public int getTrafficClass() throws SocketException {
    return delegate.getTrafficClass();
  }

  @Override
  public void setReuseAddress(final boolean on) throws SocketException {
    delegate.setReuseAddress(on);
  }

  @Override
  public boolean getReuseAddress() throws SocketException {
    return delegate.getReuseAddress();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public void shutdownInput() throws IOException {
    delegate.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    delegate.shutdownOutput();
  }

  @Override
  public boolean isConnected() {
    return delegate.isConnected();
  }

  @Override
  public boolean isBound() {
    return delegate.isBound();
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public boolean isInputShutdown() {
    return delegate.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return delegate.isOutputShutdown();
  }

  @Override
  public void setPerformancePreferences(
      final int connectionTime, final int latency, final int bandwidth) {
    delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}

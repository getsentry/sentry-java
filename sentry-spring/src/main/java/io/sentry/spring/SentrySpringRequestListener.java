package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Breadcrumb;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.util.StreamUtils;

/** Pushes new {@link io.sentry.Scope} on each incoming HTTP request. */
@Open
@Deprecated
public class SentrySpringRequestListener implements ServletRequestListener, Ordered {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver requestResolver;

  /**
   * Creates a new instance of {@link SentrySpringRequestListener}. Used in traditional servlet
   * containers with {@link SentrySpringServletContainerInitializer}.
   */
  public SentrySpringRequestListener() {
    this(HubAdapter.getInstance());
  }

  /**
   * Creates a new instance of {@link SentrySpringRequestListener}. Used together with Spring Boot
   * or with embedded servlet containers.
   *
   * @param hub - the hub
   * @param requestResolver - the request resolver
   */
  public SentrySpringRequestListener(
      final @NotNull IHub hub, final @NotNull SentryRequestResolver requestResolver) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver are required");
  }

  SentrySpringRequestListener(final @NotNull IHub hub) {
    this(hub, new SentryRequestResolver(hub));
  }

  @Override
  public void requestDestroyed(ServletRequestEvent sre) {
    hub.popScope();
  }

  @Override
  public void requestInitialized(ServletRequestEvent sre) {
    hub.pushScope();

    final ServletRequest servletRequest = sre.getServletRequest();
    if (servletRequest instanceof HttpServletRequest) {
      final HttpServletRequest request = resolveHttpServletRequest(sre);
      hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));

      hub.configureScope(
          scope -> {
            scope.setRequest(requestResolver.resolveSentryRequest(request));
            scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request));
          });
    }
  }

  private HttpServletRequest resolveHttpServletRequest(ServletRequestEvent sre) {
    try {
      return new CachedBodyHttpServletRequest((HttpServletRequest) sre.getServletRequest());
    } catch (IOException e) {
      return (HttpServletRequest) sre.getServletRequest();
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
      super(request);
      InputStream requestInputStream = request.getInputStream();
      this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
    }

    @Override
    public ServletInputStream getInputStream() {
      return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
      // Create a reader from cachedContent
      // and return it
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
      return new BufferedReader(
          new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
    }
  }

  static final class CachedBodyServletInputStream extends ServletInputStream {

    private final InputStream cachedBodyInputStream;

    public CachedBodyServletInputStream(byte[] cachedBody) {
      this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    @SuppressWarnings("EmptyCatch")
    public boolean isFinished() {
      try {
        return cachedBodyInputStream.available() == 0;
      } catch (IOException e) {
      }
      return false;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
      return cachedBodyInputStream.read();
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
      return cachedBodyInputStream.read(b, off, len);
    }
  }
}

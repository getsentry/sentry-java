package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import io.sentry.Breadcrumb;
import io.sentry.IHub;

@Open
public class SentrySpringFilter extends OncePerRequestFilter implements Ordered {
  private final @NotNull IHub hub;
  private final @NotNull SentryRequestResolver requestResolver;

  public SentrySpringFilter(@NotNull IHub hub, @NotNull SentryRequestResolver requestResolver) {
    this.hub = hub;
    this.requestResolver = requestResolver;
  }

  @Override
  protected void doFilterInternal(final @NotNull HttpServletRequest servletRequest, final @NotNull HttpServletResponse response, final @NotNull FilterChain filterChain) throws ServletException, IOException {
    final HttpServletRequest request = resolveHttpServletRequest(servletRequest);
    try {
      hub.pushScope();
      hub.addBreadcrumb(Breadcrumb.http(request.getRequestURI(), request.getMethod()));
      hub.configureScope(
        scope -> {
          scope.setRequest(requestResolver.resolveSentryRequest(request));
          scope.addEventProcessor(new SentryRequestHttpServletRequestProcessor(request));
        });
    } finally {
      filterChain.doFilter(request, response);
    }
  }

  private HttpServletRequest resolveHttpServletRequest(HttpServletRequest request) {
    try {
      return new SentrySpringRequestListener.CachedBodyHttpServletRequest(request);
    } catch (IOException e) {
      return request;
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
      return new SentrySpringRequestListener.CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
      // Create a reader from cachedContent
      // and return it
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
      return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
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

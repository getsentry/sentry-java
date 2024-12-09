package io.sentry.spring;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.util.ContentCachingRequestWrapper;

public final class SentryContentCachingRequestWrapper extends ContentCachingRequestWrapper {
  public SentryContentCachingRequestWrapper(HttpServletRequest request) {
    super(request);
  }

  public SentryContentCachingRequestWrapper(HttpServletRequest request, int contentCacheLimit) {
    super(request, contentCacheLimit);
  }

  @Override
  public @NotNull ServletInputStream getInputStream() throws IOException {
    final ServletInputStream originalInputStream = super.getInputStream();
    if (originalInputStream.isFinished()) {
      return new CachedBodyServletInputStream(getContentAsByteArray());
    } else {
      return originalInputStream;
    }
  }

  @Override
  public @NotNull BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }

  private static final class CachedBodyServletInputStream extends ServletInputStream {

    private final @NotNull InputStream cachedBodyInputStream;

    public CachedBodyServletInputStream(final @NotNull byte[] cachedBody) {
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
    public void setReadListener(final @Nullable ReadListener readListener) {
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

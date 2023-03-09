package io.sentry.spring.jakarta;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CachedBodyServletInputStream extends ServletInputStream {

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

package io.sentry.spring;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StreamUtils;

final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final @NotNull byte[] cachedBody;

  public CachedBodyHttpServletRequest(final @NotNull HttpServletRequest request)
      throws IOException {
    super(request);
    this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
  }

  @Override
  public @NotNull ServletInputStream getInputStream() {
    return new CachedBodyServletInputStream(this.cachedBody);
  }

  @Override
  public @NotNull BufferedReader getReader() {
    return new BufferedReader(
        new InputStreamReader(new ByteArrayInputStream(this.cachedBody), StandardCharsets.UTF_8));
  }
}

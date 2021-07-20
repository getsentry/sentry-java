package io.sentry.spring;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StreamUtils;

final class RequestPayloadExtractor {

  @Nullable
  String extract(final @NotNull HttpServletRequest request, final @NotNull SentryOptions options) {
    // request body can be read only once from the stream
    // original request can be replaced with CachedBodyHttpServletRequest in SentrySpringFilter
    if (request instanceof CachedBodyHttpServletRequest) {
      try {
        final byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        return new String(body, StandardCharsets.UTF_8);
      } catch (IOException e) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to set request body", e);
        return null;
      }
    } else {
      return null;
    }
  }
}

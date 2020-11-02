package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.util.Objects;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.NamedThreadLocal;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.util.UriTemplateHandler;

@Open
class SentrySpanClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  private static final @NotNull ThreadLocal<Deque<String>> urlTemplate =
      new UrlTemplateThreadLocal();
  private final @NotNull IHub hub;

  public SentrySpanClientHttpRequestInterceptor(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public @NotNull ClientHttpResponse intercept(
      @NotNull HttpRequest request,
      @NotNull byte[] body,
      @NotNull ClientHttpRequestExecution execution)
      throws IOException {
    final ISpan activeSpan = resolveActiveSpan();
    if (activeSpan == null) {
      return execution.execute(request, body);
    }

    final ISpan span = activeSpan.startChild();
    span.setDescription(
        request.getMethodValue() + " " + ensureLeadingSlash(urlTemplate.get().poll()));
    span.setOp("http");

    final SentryTraceHeader sentryTraceHeader = activeSpan.toSentryTrace();
    request.getHeaders().add(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
    try {
      return execution.execute(request, body);
    } finally {
      span.finish();
      if (urlTemplate.get().isEmpty()) {
        urlTemplate.remove();
      }
    }
  }

  // TODO: this method ideally gets extracted or moves to Hub itself
  private @Nullable ISpan resolveActiveSpan() {
    final AtomicReference<ISpan> spanRef = new AtomicReference<>();

    hub.configureScope(
        scope -> {
          final ISpan span = scope.getSpan();

          if (span != null) {
            spanRef.set(span);
          }
        });

    return spanRef.get();
  }

  UriTemplateHandler createUriTemplateHandler(final @NotNull UriTemplateHandler delegate) {
    return new UriTemplateHandler() {

      @Override
      public URI expand(String url, Map<String, ?> arguments) {
        urlTemplate.get().push(url);
        return delegate.expand(url, arguments);
      }

      @Override
      public URI expand(String url, Object... arguments) {
        urlTemplate.get().push(url);
        return delegate.expand(url, arguments);
      }
    };
  }

  private static @NotNull String ensureLeadingSlash(final @Nullable String url) {
    return (url == null || url.startsWith("/")) ? url : "/" + url;
  }

  private static final class UrlTemplateThreadLocal extends NamedThreadLocal<Deque<String>> {

    private UrlTemplateThreadLocal() {
      super("Rest Template URL Template");
    }

    @Override
    protected Deque<String> initialValue() {
      return new ArrayDeque<>();
    }
  }
}

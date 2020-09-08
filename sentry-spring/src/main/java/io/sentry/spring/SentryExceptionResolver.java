package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.IHub;
import io.sentry.core.SentryEvent;
import io.sentry.core.SentryLevel;
import io.sentry.core.exception.ExceptionMechanismException;
import io.sentry.core.protocol.Mechanism;
import io.sentry.core.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@link HandlerExceptionResolver} implementation that will record any exception that a Spring
 * {@link org.springframework.web.servlet.mvc.Controller} throws to Sentry. It then returns null,
 * which will let the other (default or custom) exception resolvers handle the actual error.
 */
@Open
public class SentryExceptionResolver implements HandlerExceptionResolver, Ordered {
  private final @NotNull IHub hub;

  public SentryExceptionResolver(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public @Nullable ModelAndView resolveException(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @Nullable Object handler,
      final @NotNull Exception ex) {

    final Mechanism mechanism = new Mechanism();
    mechanism.setHandled(false);
    final Throwable throwable =
        new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
    final SentryEvent event = new SentryEvent(throwable);
    event.setLevel(SentryLevel.FATAL);
    hub.captureEvent(event);

    // null = run other HandlerExceptionResolvers to actually handle the exception
    return null;
  }

  @Override
  public int getOrder() {
    // ensure this resolver runs first so that all exceptions are reported
    return Integer.MIN_VALUE;
  }
}

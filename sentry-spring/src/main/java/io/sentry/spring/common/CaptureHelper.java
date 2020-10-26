package io.sentry.spring.common;

import io.sentry.IHub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;

/** Capture unhandled exceptions. Common to webflux and SpringMVC */
public final class CaptureHelper {

  public static void captureUnhandled(IHub hub, Throwable ex) {
    final Mechanism mechanism = new Mechanism();
    mechanism.setHandled(false);
    final Throwable throwable =
        new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
    final SentryEvent event = new SentryEvent(throwable);
    event.setLevel(SentryLevel.FATAL);
    hub.captureEvent(event);
  }
}

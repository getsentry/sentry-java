package io.sentry.spring.jakarta.webflux;

import org.jetbrains.annotations.ApiStatus;

import io.micrometer.context.ThreadLocalAccessor;
import io.sentry.IHub;
import io.sentry.NoOpHub;
import io.sentry.Sentry;

@ApiStatus.Experimental
public final class SentryReactorThreadLocalAccessor implements ThreadLocalAccessor<IHub> {

  public static final String KEY = "sentry-hub";

  @Override
  public Object key() {
//    Sentry.getCurrentHub().getOptions().getLogger().log(SentryLevel.WARNING, "get");
    return KEY;
  }

  @Override
  public IHub getValue() {
//    Sentry.getCurrentHub().getOptions().getLogger().log(SentryLevel.WARNING, "get value");
    return Sentry.getCurrentHub();
  }

  @Override
  public void setValue(IHub value) {
//    Sentry.getCurrentHub().getOptions().getLogger().log(SentryLevel.WARNING, "set value " + value);
    Sentry.setCurrentHub(value);
  }

  @Override
  public void reset() {
//    Sentry.getCurrentHub().getOptions().getLogger().log(SentryLevel.WARNING, "reset");
    Sentry.setCurrentHub(NoOpHub.getInstance());
  }

//  @Override
//  public void restore(IHub previousValue) {
////    Sentry.getCurrentHub().getOptions().getLogger().log(SentryLevel.WARNING, "restore value " + previousValue);
//    ThreadLocalAccessor.super.restore(previousValue);
//  }
}

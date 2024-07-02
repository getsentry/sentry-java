package io.sentry.spring.jakarta.webflux;

import io.micrometer.context.ThreadLocalAccessor;
import io.sentry.IHub;
import io.sentry.NoOpHub;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public final class SentryReactorThreadLocalAccessor implements ThreadLocalAccessor<IHub> {

  public static final String KEY = "sentry-hub";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public IHub getValue() {
    return Sentry.getCurrentHub();
  }

  @Override
  public void setValue(IHub value) {
    Sentry.setCurrentHub(value);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void reset() {
    Sentry.setCurrentHub(NoOpHub.getInstance());
  }
}

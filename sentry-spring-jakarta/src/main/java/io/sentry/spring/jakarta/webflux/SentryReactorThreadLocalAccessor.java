package io.sentry.spring.jakarta.webflux;

import io.micrometer.context.ThreadLocalAccessor;
import io.sentry.IScopes;
import io.sentry.NoOpScopes;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public final class SentryReactorThreadLocalAccessor implements ThreadLocalAccessor<IScopes> {

  public static final String KEY = "sentry-scopes";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public IScopes getValue() {
    return Sentry.getCurrentScopes();
  }

  @Override
  public void setValue(IScopes value) {
    Sentry.setCurrentScopes(value);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void reset() {
    Sentry.setCurrentScopes(NoOpScopes.getInstance());
  }
}

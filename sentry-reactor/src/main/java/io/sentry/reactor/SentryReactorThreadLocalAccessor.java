package io.sentry.reactor;

import io.micrometer.context.ThreadLocalAccessor;
import io.sentry.IScopes;
import io.sentry.NoOpScopes;
import io.sentry.Sentry;

public final class SentryReactorThreadLocalAccessor implements ThreadLocalAccessor<IScopes> {

  public static final String KEY = "sentry-scopes";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public IScopes getValue() {
    if (Sentry.hasScopes()) {
      return Sentry.getCurrentScopes();
    } else {
      return NoOpScopes.getInstance();
    }
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

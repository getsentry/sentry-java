package io.sentry.opentelemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;

public final class SentryContextStorageProvider implements ContextStorageProvider {
  @Override
  public ContextStorage get() {
    return new SentryContextStorage(new SentryOtelThreadLocalStorage());
  }
}

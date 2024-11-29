package io.sentry.opentelemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;

public final class SentryContextStorageProvider implements ContextStorageProvider {
  @Override
  public ContextStorage get() {
    System.out.println("hello from SentryContextStorageProvider");
    return new SentryContextStorage(new SentryOtelThreadLocalStorage());
  }
}

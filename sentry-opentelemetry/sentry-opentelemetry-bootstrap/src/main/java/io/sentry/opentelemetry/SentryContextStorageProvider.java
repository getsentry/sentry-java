package io.sentry.opentelemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.sentry.util.ExceptionUtils;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;

public final class SentryContextStorageProvider implements ContextStorageProvider {
  @Override
  public ContextStorage get() {
    return new SentryContextStorage(findStorageToWrap());
  }

  private @NotNull ContextStorage findStorageToWrap() {
    try {
      ServiceLoader<ContextStorageProvider> serviceLoader =
          ServiceLoader.load(ContextStorageProvider.class);
      Iterator<ContextStorageProvider> iterator = serviceLoader.iterator();
      while (iterator.hasNext()) {
        ContextStorageProvider contextStorageProvider = iterator.next();
        if (!(contextStorageProvider instanceof SentryContextStorageProvider)) {
          return contextStorageProvider.get();
        }
      }
    } catch (LinkageError ignored) {
      // a provider needs a class that is missing or has an incompatible version, use fallback
    } catch (Throwable t) {
      // Other providers are third-party code, so we can't know what they throw. Use fallback.
      ExceptionUtils.rethrowIfFatal(t);
    }

    // using default / fallback storage
    return new SentryOtelThreadLocalStorage();
  }
}

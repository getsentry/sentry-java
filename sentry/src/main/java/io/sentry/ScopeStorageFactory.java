package io.sentry;

import io.sentry.util.LoadClass;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScopeStorageFactory {

  private static final String OTEL_SCOPE_STORAGE =
      "io.sentry.opentelemetry.OtelContextScopeStorage";

  public static @NotNull ScopeStorage create() {
    // TODO testable
    LoadClass loadClass = new LoadClass();
    // TODO logger?
    NoOpLogger logger = NoOpLogger.getInstance();
    if (loadClass.isClassAvailable(OTEL_SCOPE_STORAGE, logger)) {
      Class<?> otelScopeStorageClazz = loadClass.loadClass(OTEL_SCOPE_STORAGE, logger);
      if (otelScopeStorageClazz != null) {
        try {
          final @Nullable Object otelScopeStorage =
              otelScopeStorageClazz.getDeclaredConstructor().newInstance();
          if (otelScopeStorage != null && otelScopeStorage instanceof ScopeStorage) {
            return (ScopeStorage) otelScopeStorage;
          }
        } catch (InstantiationException e) {
          // TODO log
        } catch (IllegalAccessException e) {
          // TODO log
        } catch (InvocationTargetException e) {
          // TODO log
        } catch (NoSuchMethodException e) {
          // TODO log
        }
      }
    }

    return new DefaultScopeStorage();
  }
}

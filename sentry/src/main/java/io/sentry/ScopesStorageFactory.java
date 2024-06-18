package io.sentry;

import io.sentry.util.LoadClass;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScopesStorageFactory {

  private static final String OTEL_SCOPES_STORAGE =
      "io.sentry.opentelemetry.OtelContextScopesStorage";

  public static @NotNull IScopesStorage create(
      final @NotNull LoadClass loadClass, final @NotNull ILogger logger) {
    if (loadClass.isClassAvailable(OTEL_SCOPES_STORAGE, logger)) {
      Class<?> otelScopesStorageClazz = loadClass.loadClass(OTEL_SCOPES_STORAGE, logger);
      if (otelScopesStorageClazz != null) {
        try {
          final @Nullable Object otelScopesStorage =
              otelScopesStorageClazz.getDeclaredConstructor().newInstance();
          if (otelScopesStorage != null && otelScopesStorage instanceof IScopesStorage) {
            return (IScopesStorage) otelScopesStorage;
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

    return new DefaultScopesStorage();
  }
}

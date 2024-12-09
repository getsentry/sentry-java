package io.sentry;

import io.sentry.util.LoadClass;
import io.sentry.util.Platform;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ScopesStorageFactory {

  private static final String OTEL_SCOPES_STORAGE =
      "io.sentry.opentelemetry.OtelContextScopesStorage";

  public static @NotNull IScopesStorage create(
      final @NotNull LoadClass loadClass, final @NotNull ILogger logger) {
    final @NotNull IScopesStorage storage = createInternal(loadClass, logger);
    storage.init();
    return storage;
  }

  private static @NotNull IScopesStorage createInternal(
      final @NotNull LoadClass loadClass, final @NotNull ILogger logger) {
    if (Platform.isJvm()) {
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
    }

    return new DefaultScopesStorage();
  }
}

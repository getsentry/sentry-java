package io.sentry;

import io.sentry.util.LoadClass;
import io.sentry.util.Platform;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanFactoryFactory {

  private static final String OTEL_SPAN_FACTORY = "io.sentry.opentelemetry.OtelSpanFactory";

  public static @NotNull ISpanFactory create(
      final @NotNull LoadClass loadClass, final @NotNull ILogger logger) {
    if (Platform.isJvm()) {
      if (loadClass.isClassAvailable(OTEL_SPAN_FACTORY, logger)) {
        Class<?> otelSpanFactoryClazz = loadClass.loadClass(OTEL_SPAN_FACTORY, logger);
        if (otelSpanFactoryClazz != null) {
          try {
            final @Nullable Object otelSpanFactory =
                otelSpanFactoryClazz.getDeclaredConstructor().newInstance();
            if (otelSpanFactory != null && otelSpanFactory instanceof ISpanFactory) {
              return (ISpanFactory) otelSpanFactory;
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

    return new DefaultSpanFactory();
  }
}

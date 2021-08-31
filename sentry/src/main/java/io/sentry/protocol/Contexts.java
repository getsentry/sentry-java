package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.SpanContext;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Contexts extends ConcurrentHashMap<String, Object> implements JsonSerializable {
  private static final long serialVersionUID = 252445813254943011L;

  public Contexts() {}

  public Contexts(final @NotNull Contexts contexts) {
    for (Map.Entry<String, Object> entry : contexts.entrySet()) {
      if (entry != null) {
        Object value = entry.getValue();
        if (App.TYPE.equals(entry.getKey()) && value instanceof App) {
          this.setApp(new App((App) value));
        } else if (Browser.TYPE.equals(entry.getKey()) && value instanceof Browser) {
          this.setBrowser(new Browser((Browser) value));
        } else if (Device.TYPE.equals(entry.getKey()) && value instanceof Device) {
          this.setDevice(new Device((Device) value));
        } else if (OperatingSystem.TYPE.equals(entry.getKey())
            && value instanceof OperatingSystem) {
          this.setOperatingSystem(new OperatingSystem((OperatingSystem) value));
        } else if (SentryRuntime.TYPE.equals(entry.getKey()) && value instanceof SentryRuntime) {
          this.setRuntime(new SentryRuntime((SentryRuntime) value));
        } else if (Gpu.TYPE.equals(entry.getKey()) && value instanceof Gpu) {
          this.setGpu(new Gpu((Gpu) value));
        } else if (SpanContext.TYPE.equals(entry.getKey()) && value instanceof SpanContext) {
          this.setTrace(new SpanContext((SpanContext) value));
        } else {
          this.put(entry.getKey(), value);
        }
      }
    }
  }

  private @Nullable <T> T toContextType(final @NotNull String key, final @NotNull Class<T> clazz) {
    final Object item = get(key);
    return clazz.isInstance(item) ? clazz.cast(item) : null;
  }

  public @Nullable SpanContext getTrace() {
    return toContextType(SpanContext.TYPE, SpanContext.class);
  }

  public void setTrace(final @Nullable SpanContext traceContext) {
    Objects.requireNonNull(traceContext, "traceContext is required");
    this.put(SpanContext.TYPE, traceContext);
  }

  public @Nullable App getApp() {
    return toContextType(App.TYPE, App.class);
  }

  public void setApp(final @NotNull App app) {
    this.put(App.TYPE, app);
  }

  public @Nullable Browser getBrowser() {
    return toContextType(Browser.TYPE, Browser.class);
  }

  public void setBrowser(final @NotNull Browser browser) {
    this.put(Browser.TYPE, browser);
  }

  public @Nullable Device getDevice() {
    return toContextType(Device.TYPE, Device.class);
  }

  public void setDevice(final @NotNull Device device) {
    this.put(Device.TYPE, device);
  }

  public @Nullable OperatingSystem getOperatingSystem() {
    return toContextType(OperatingSystem.TYPE, OperatingSystem.class);
  }

  public void setOperatingSystem(final @NotNull OperatingSystem operatingSystem) {
    this.put(OperatingSystem.TYPE, operatingSystem);
  }

  public @Nullable SentryRuntime getRuntime() {
    return toContextType(SentryRuntime.TYPE, SentryRuntime.class);
  }

  public void setRuntime(final @NotNull SentryRuntime runtime) {
    this.put(SentryRuntime.TYPE, runtime);
  }

  public @Nullable Gpu getGpu() {
    return toContextType(Gpu.TYPE, Gpu.class);
  }

  public void setGpu(final @NotNull Gpu gpu) {
    this.put(Gpu.TYPE, gpu);
  }

  // region json

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<Contexts> {

    @Override
    public @NotNull Contexts deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return new Contexts();
    }
  }

  // endregion
}

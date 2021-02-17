package io.sentry.protocol;

import io.sentry.SpanContext;
import io.sentry.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Contexts extends ConcurrentHashMap<String, Object> implements Cloneable {
  private static final long serialVersionUID = 252445813254943011L;

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

  @Override
  public @NotNull Contexts clone() throws CloneNotSupportedException {
    final Contexts clone = new Contexts();
    for (Map.Entry<String, Object> entry : entrySet()) {
      if (entry != null) {
        Object value = entry.getValue();
        if (App.TYPE.equals(entry.getKey()) && value instanceof App) {
          clone.setApp(((App) value).clone());
        } else if (Browser.TYPE.equals(entry.getKey()) && value instanceof Browser) {
          clone.setBrowser(((Browser) value).clone());
        } else if (Device.TYPE.equals(entry.getKey()) && value instanceof Device) {
          clone.setDevice(((Device) value).clone());
        } else if (OperatingSystem.TYPE.equals(entry.getKey())
            && value instanceof OperatingSystem) {
          clone.setOperatingSystem(((OperatingSystem) value).clone());
        } else if (SentryRuntime.TYPE.equals(entry.getKey()) && value instanceof SentryRuntime) {
          clone.setRuntime(((SentryRuntime) value).clone());
        } else if (Gpu.TYPE.equals(entry.getKey()) && value instanceof Gpu) {
          clone.setGpu(((Gpu) value).clone());
        } else if (SpanContext.TYPE.equals(entry.getKey()) && value instanceof SpanContext) {
          clone.setTrace(((SpanContext) value).clone());
        } else {
          clone.put(entry.getKey(), value);
        }
      }
    }
    return clone;
  }
}

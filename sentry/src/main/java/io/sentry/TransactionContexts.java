package io.sentry;

import io.sentry.protocol.App;
import io.sentry.protocol.Browser;
import io.sentry.protocol.Device;
import io.sentry.protocol.Gpu;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.SentryRuntime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransactionContexts {
  private @Nullable App app;
  private @Nullable Browser browser;
  private @Nullable Device device;
  private @Nullable OperatingSystem operatingSystem;
  private @Nullable SentryRuntime runtime;
  private @Nullable Gpu gpu;
  private final @NotNull SpanContext trace;
  private final @NotNull Map<String, Object> other = new ConcurrentHashMap<>();

  public TransactionContexts(final @NotNull SpanContext trace) {
    this.trace = trace;
  }

  public @Nullable App getApp() {
    return app;
  }

  public void setApp(@Nullable App app) {
    this.app = app;
  }

  public @Nullable Browser getBrowser() {
    return browser;
  }

  public void setBrowser(@Nullable Browser browser) {
    this.browser = browser;
  }

  public @Nullable Device getDevice() {
    return device;
  }

  public void setDevice(@Nullable Device device) {
    this.device = device;
  }

  public @Nullable OperatingSystem getOperatingSystem() {
    return operatingSystem;
  }

  public void setOperatingSystem(@Nullable OperatingSystem operatingSystem) {
    this.operatingSystem = operatingSystem;
  }

  public @Nullable SentryRuntime getRuntime() {
    return runtime;
  }

  public void setRuntime(@Nullable SentryRuntime runtime) {
    this.runtime = runtime;
  }

  public @Nullable Gpu getGpu() {
    return gpu;
  }

  public void setGpu(@Nullable Gpu gpu) {
    this.gpu = gpu;
  }

  public @NotNull SpanContext getTrace() {
    return trace;
  }

  public Object get(final @NotNull String key) {
    return other.get(key);
  }

  public @Nullable Object set(final @NotNull String key, final @NotNull Object value) {
    return other.put(key, value);
  }

  @ApiStatus.Internal
  public @NotNull Map<String, Object> getOther() {
    return other;
  }
}

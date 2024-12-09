package io.sentry.protocol;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SpanContext;
import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class Contexts implements JsonSerializable {
  private static final long serialVersionUID = 252445813254943011L;
  public static final String REPLAY_ID = "replay_id";

  private final @NotNull ConcurrentHashMap<String, Object> internalStorage =
      new ConcurrentHashMap<>();

  /** Response lock, Ops should be atomic */
  protected final @NotNull AutoClosableReentrantLock responseLock = new AutoClosableReentrantLock();

  public Contexts() {}

  public Contexts(final @NotNull Contexts contexts) {
    for (final Map.Entry<String, Object> entry : contexts.entrySet()) {
      if (entry != null) {
        final Object value = entry.getValue();
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
        } else if (Response.TYPE.equals(entry.getKey()) && value instanceof Response) {
          this.setResponse(new Response((Response) value));
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

  public void setTrace(final @NotNull SpanContext traceContext) {
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

  public @Nullable Response getResponse() {
    return toContextType(Response.TYPE, Response.class);
  }

  public void withResponse(HintUtils.SentryConsumer<Response> callback) {
    try (final @NotNull ISentryLifecycleToken ignored = responseLock.acquire()) {
      final @Nullable Response response = getResponse();
      if (response != null) {
        callback.accept(response);
      } else {
        final @NotNull Response newResponse = new Response();
        setResponse(newResponse);
        callback.accept(newResponse);
      }
    }
  }

  public void setResponse(final @NotNull Response response) {
    try (final @NotNull ISentryLifecycleToken ignored = responseLock.acquire()) {
      this.put(Response.TYPE, response);
    }
  }

  public int size() {
    // since this used to extend map
    return internalStorage.size();
  }

  public int getSize() {
    // for kotlin .size
    return size();
  }

  public boolean isEmpty() {
    return internalStorage.isEmpty();
  }

  public boolean containsKey(final @NotNull Object key) {
    return internalStorage.containsKey(key);
  }

  public @Nullable Object get(final @NotNull Object key) {
    return internalStorage.get(key);
  }

  public @Nullable Object put(final @NotNull String key, final @Nullable Object value) {
    return internalStorage.put(key, value);
  }

  public @Nullable Object set(final @NotNull String key, final @Nullable Object value) {
    return put(key, value);
  }

  public @Nullable Object remove(final @NotNull Object key) {
    return internalStorage.remove(key);
  }

  public @NotNull Enumeration<String> keys() {
    return internalStorage.keys();
  }

  public @NotNull Set<Map.Entry<String, Object>> entrySet() {
    return internalStorage.entrySet();
  }

  public void putAll(Map<? extends String, ? extends Object> m) {
    internalStorage.putAll(m);
  }

  public void putAll(final @NotNull Contexts contexts) {
    internalStorage.putAll(contexts.internalStorage);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof Contexts) {
      final @NotNull Contexts otherContexts = (Contexts) obj;
      return internalStorage.equals(otherContexts.internalStorage);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return internalStorage.hashCode();
  }

  // region json

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    // Serialize in alphabetical order to keep determinism.
    final List<String> sortedKeys = Collections.list(keys());
    Collections.sort(sortedKeys);
    for (final String key : sortedKeys) {
      final Object value = get(key);
      if (value != null) {
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<Contexts> {

    @Override
    public @NotNull Contexts deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      final Contexts contexts = new Contexts();
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case App.TYPE:
            contexts.setApp(new App.Deserializer().deserialize(reader, logger));
            break;
          case Browser.TYPE:
            contexts.setBrowser(new Browser.Deserializer().deserialize(reader, logger));
            break;
          case Device.TYPE:
            contexts.setDevice(new Device.Deserializer().deserialize(reader, logger));
            break;
          case Gpu.TYPE:
            contexts.setGpu(new Gpu.Deserializer().deserialize(reader, logger));
            break;
          case OperatingSystem.TYPE:
            contexts.setOperatingSystem(
                new OperatingSystem.Deserializer().deserialize(reader, logger));
            break;
          case SentryRuntime.TYPE:
            contexts.setRuntime(new SentryRuntime.Deserializer().deserialize(reader, logger));
            break;
          case SpanContext.TYPE:
            contexts.setTrace(new SpanContext.Deserializer().deserialize(reader, logger));
            break;
          case Response.TYPE:
            contexts.setResponse(new Response.Deserializer().deserialize(reader, logger));
            break;
          default:
            Object object = reader.nextObjectOrNull();
            if (object != null) {
              contexts.put(nextName, object);
            }
            break;
        }
      }
      reader.endObject();
      return contexts;
    }
  }

  // endregion
}

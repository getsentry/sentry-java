package io.sentry;

import io.sentry.protocol.App;
import io.sentry.protocol.Browser;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.Device;
import io.sentry.protocol.FeatureFlags;
import io.sentry.protocol.Gpu;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.Response;
import io.sentry.protocol.SentryRuntime;
import io.sentry.protocol.Spring;
import io.sentry.util.HintUtils;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CombinedContextsView extends Contexts {

  private static final long serialVersionUID = 3585992094653318439L;
  private final @NotNull Contexts globalContexts;
  private final @NotNull Contexts isolationContexts;
  private final @NotNull Contexts currentContexts;

  private final @NotNull ScopeType defaultScopeType;

  public CombinedContextsView(
      final @NotNull Contexts globalContexts,
      final @NotNull Contexts isolationContexts,
      final @NotNull Contexts currentContexts,
      final @NotNull ScopeType defaultScopeType) {
    this.globalContexts = globalContexts;
    this.isolationContexts = isolationContexts;
    this.currentContexts = currentContexts;
    this.defaultScopeType = defaultScopeType;
  }

  @Override
  public @Nullable SpanContext getTrace() {
    final @Nullable SpanContext current = currentContexts.getTrace();
    if (current != null) {
      return current;
    }
    final @Nullable SpanContext isolation = isolationContexts.getTrace();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getTrace();
  }

  @Override
  public void setTrace(@NotNull SpanContext traceContext) {
    getDefaultContexts().setTrace(traceContext);
  }

  private @NotNull Contexts getDefaultContexts() {
    switch (defaultScopeType) {
      case CURRENT:
        return currentContexts;
      case ISOLATION:
        return isolationContexts;
      case GLOBAL:
        return globalContexts;
      default:
        return currentContexts;
    }
  }

  @Override
  public @Nullable App getApp() {
    final @Nullable App current = currentContexts.getApp();
    if (current != null) {
      return current;
    }
    final @Nullable App isolation = isolationContexts.getApp();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getApp();
  }

  @Override
  public void setApp(@NotNull App app) {
    getDefaultContexts().setApp(app);
  }

  @Override
  public @Nullable Browser getBrowser() {
    final @Nullable Browser current = currentContexts.getBrowser();
    if (current != null) {
      return current;
    }
    final @Nullable Browser isolation = isolationContexts.getBrowser();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getBrowser();
  }

  @Override
  public void setBrowser(@NotNull Browser browser) {
    getDefaultContexts().setBrowser(browser);
  }

  @Override
  public @Nullable Device getDevice() {
    final @Nullable Device current = currentContexts.getDevice();
    if (current != null) {
      return current;
    }
    final @Nullable Device isolation = isolationContexts.getDevice();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getDevice();
  }

  @Override
  public void setDevice(@NotNull Device device) {
    getDefaultContexts().setDevice(device);
  }

  @Override
  public @Nullable OperatingSystem getOperatingSystem() {
    final @Nullable OperatingSystem current = currentContexts.getOperatingSystem();
    if (current != null) {
      return current;
    }
    final @Nullable OperatingSystem isolation = isolationContexts.getOperatingSystem();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getOperatingSystem();
  }

  @Override
  public void setOperatingSystem(@NotNull OperatingSystem operatingSystem) {
    getDefaultContexts().setOperatingSystem(operatingSystem);
  }

  @Override
  public @Nullable SentryRuntime getRuntime() {
    final @Nullable SentryRuntime current = currentContexts.getRuntime();
    if (current != null) {
      return current;
    }
    final @Nullable SentryRuntime isolation = isolationContexts.getRuntime();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getRuntime();
  }

  @Override
  public void setRuntime(@NotNull SentryRuntime runtime) {
    getDefaultContexts().setRuntime(runtime);
  }

  @Override
  public @Nullable Gpu getGpu() {
    final @Nullable Gpu current = currentContexts.getGpu();
    if (current != null) {
      return current;
    }
    final @Nullable Gpu isolation = isolationContexts.getGpu();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getGpu();
  }

  @Override
  public void setGpu(@NotNull Gpu gpu) {
    getDefaultContexts().setGpu(gpu);
  }

  @Override
  public @Nullable Response getResponse() {
    final @Nullable Response current = currentContexts.getResponse();
    if (current != null) {
      return current;
    }
    final @Nullable Response isolation = isolationContexts.getResponse();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getResponse();
  }

  @Override
  public void withResponse(HintUtils.SentryConsumer<Response> callback) {
    if (currentContexts.getResponse() != null) {
      currentContexts.withResponse(callback);
    } else if (isolationContexts.getResponse() != null) {
      isolationContexts.withResponse(callback);
    } else if (globalContexts.getResponse() != null) {
      globalContexts.withResponse(callback);
    } else {
      getDefaultContexts().withResponse(callback);
    }
  }

  @Override
  public void setResponse(@NotNull Response response) {
    getDefaultContexts().setResponse(response);
  }

  @Override
  public @Nullable Spring getSpring() {
    final @Nullable Spring current = currentContexts.getSpring();
    if (current != null) {
      return current;
    }
    final @Nullable Spring isolation = isolationContexts.getSpring();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getSpring();
  }

  @Override
  public void setSpring(@NotNull Spring spring) {
    getDefaultContexts().setSpring(spring);
  }

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    // these are not intended to be set on a scopes Context directly
    final @Nullable FeatureFlags current = currentContexts.getFeatureFlags();
    if (current != null) {
      return current;
    }
    final @Nullable FeatureFlags isolation = isolationContexts.getFeatureFlags();
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.getFeatureFlags();
  }

  @ApiStatus.Internal
  @Override
  /** Not intended to be set on a scopes Context directly */
  public void setFeatureFlags(@NotNull FeatureFlags spring) {
    getDefaultContexts().setFeatureFlags(spring);
  }

  @Override
  public int size() {
    return mergeContexts().size();
  }

  @Override
  public int getSize() {
    return size();
  }

  @Override
  public boolean isEmpty() {
    return globalContexts.isEmpty() && isolationContexts.isEmpty() && currentContexts.isEmpty();
  }

  @Override
  public boolean containsKey(final @Nullable Object key) {
    return globalContexts.containsKey(key)
        || isolationContexts.containsKey(key)
        || currentContexts.containsKey(key);
  }

  @Override
  public @Nullable Object get(final @Nullable Object key) {
    final @Nullable Object current = currentContexts.get(key);
    if (current != null) {
      return current;
    }
    final @Nullable Object isolation = isolationContexts.get(key);
    if (isolation != null) {
      return isolation;
    }
    return globalContexts.get(key);
  }

  @Override
  public @Nullable Object put(final @Nullable String key, final @Nullable Object value) {
    return getDefaultContexts().put(key, value);
  }

  @Override
  public @Nullable Object remove(final @Nullable Object key) {
    return getDefaultContexts().remove(key);
  }

  @Override
  public @NotNull Enumeration<String> keys() {
    return mergeContexts().keys();
  }

  @Override
  public @NotNull Set<Map.Entry<String, Object>> entrySet() {
    return mergeContexts().entrySet();
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    mergeContexts().serialize(writer, logger);
  }

  @Override
  public @Nullable Object set(@Nullable String key, @Nullable Object value) {
    return put(key, value);
  }

  @Override
  public void putAll(@Nullable Map<? extends String, ?> m) {
    getDefaultContexts().putAll(m);
  }

  @Override
  public void putAll(@Nullable Contexts contexts) {
    getDefaultContexts().putAll(contexts);
  }

  private @NotNull Contexts mergeContexts() {
    final @NotNull Contexts allContexts = new Contexts();
    allContexts.putAll(globalContexts);
    allContexts.putAll(isolationContexts);
    allContexts.putAll(currentContexts);
    return allContexts;
  }
}

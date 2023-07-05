package io.sentry;

import io.sentry.util.MapObjectWriter;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InternalSentrySdk {

  @NotNull
  public static Map<String, Object> serializeScope(@Nullable Scope scope) {
    final @NotNull Map<String, Object> data = new HashMap<>();
    if (scope == null) {
      return data;
    }

    final @NotNull SentryOptions options = scope.getOptions();
    final @NotNull ILogger logger = options.getLogger();
    final @NotNull ObjectWriter writer = new MapObjectWriter(data);

    try {
      writer.name("user").value(logger, scope.getUser());
      writer.name("contexts").value(logger, scope.getContexts());
      writer.name("tags").value(logger, scope.getTags());
      writer.name("extras").value(logger, scope.getExtras());
      writer.name("fingerprint").value(logger, scope.getFingerprint());
      writer.name("level").value(logger, scope.getLevel());
      writer.name("breadcrumbs").value(logger, scope.getBreadcrumbs());
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Could not serialize scope.", e);
      return new HashMap<>();
    }

    return data;
  }
}

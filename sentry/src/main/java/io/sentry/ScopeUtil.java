package io.sentry;

import io.sentry.util.MapObjectWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ScopeUtil {

  @NotNull
  public static Map<String, Object> serialize(@Nullable Scope scope) {
    final @NotNull Map<String, Object> data = new HashMap<>();
    if (scope == null) {
      return data;
    }

    final @NotNull SentryOptions options = scope.getOptions();
    final @NotNull ObjectWriter writer = new MapObjectWriter(data);

    try {
      serialize(writer, options, "user", scope.getUser());
      serialize(writer, options, "contexts", scope.getContexts());
      serialize(writer, options, "tags", scope.getTags());
      serialize(writer, options, "extras", scope.getExtras());
      serialize(writer, options, "fingerprint", scope.getFingerprint());
      serialize(writer, options, "level", scope.getLevel());
      serialize(writer, options, "breadcrumbs", scope.getBreadcrumbs());
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.ERROR, "Could not serialize scope");
    }

    return data;
  }

  private static void serialize(
      @NotNull ObjectWriter writer,
      @NotNull SentryOptions options,
      @NotNull String name,
      @Nullable Object data)
      throws IOException {
    writer.name(name);
    writer.value(options.getLogger(), data);
  }
}

package io.sentry.util;

import io.sentry.DataCollectionResolver;
import io.sentry.JsonObjectReader;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class GraphqlUtils {

  private GraphqlUtils() {}

  public static @Nullable String filterRequestBody(
      final @NotNull String body, final @NotNull SentryOptions options) {
    final @NotNull DataCollectionResolver resolver = options.getDataCollectionResolver();
    final boolean includeDocument = resolver.isGraphqlDocumentWithLegacyAlways();
    final boolean includeVariables = resolver.isGraphqlVariablesWithLegacyAlways();

    if (includeDocument && includeVariables) {
      return body;
    }
    if (!includeDocument && !includeVariables) {
      return null;
    }

    try (JsonObjectReader reader = new JsonObjectReader(new StringReader(body))) {
      final @Nullable Object value = reader.nextObjectOrNull();
      if (!(value instanceof Map)) {
        return null;
      }

      @SuppressWarnings("unchecked")
      final @NotNull Map<String, Object> requestBody = (Map<String, Object>) value;
      final @NotNull Map<String, Object> filtered = new LinkedHashMap<>(requestBody);
      if (!includeDocument) {
        filtered.remove("query");
      }
      if (!includeVariables) {
        filtered.remove("variables");
      }
      return options.getSerializer().serialize(filtered);
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to filter GraphQL request body.", e);
      return null;
    }
  }
}

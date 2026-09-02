package io.sentry.util;

import io.sentry.DataCollectionResolver;
import io.sentry.JsonObjectReader;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
      final @NotNull Object filtered;
      if (value instanceof Map) {
        @SuppressWarnings("unchecked")
        final @NotNull Map<String, Object> requestBody = (Map<String, Object>) value;
        filtered = filterRequest(requestBody, includeDocument, includeVariables);
      } else if (value instanceof List) {
        final @NotNull List<Map<String, Object>> filteredBatch = new ArrayList<>();
        for (final @Nullable Object item : (List<?>) value) {
          if (!(item instanceof Map)) {
            return null;
          }
          @SuppressWarnings("unchecked")
          final @NotNull Map<String, Object> requestBody = (Map<String, Object>) item;
          filteredBatch.add(filterRequest(requestBody, includeDocument, includeVariables));
        }
        filtered = filteredBatch;
      } else {
        return null;
      }
      final @NotNull StringWriter writer = new StringWriter();
      options.getSerializer().serialize(filtered, writer);
      return writer.toString();
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to filter GraphQL request body.", e);
      return null;
    }
  }

  private static @NotNull Map<String, Object> filterRequest(
      final @NotNull Map<String, Object> request,
      final boolean includeDocument,
      final boolean includeVariables) {
    final @NotNull Map<String, Object> filtered = new LinkedHashMap<>(request);
    if (!includeDocument) {
      filtered.remove("query");
    }
    if (!includeVariables) {
      filtered.remove("variables");
    }
    return filtered;
  }
}

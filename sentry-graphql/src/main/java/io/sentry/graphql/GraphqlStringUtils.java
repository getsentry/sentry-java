package io.sentry.graphql;

import graphql.execution.MergedField;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import io.sentry.util.StringUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class GraphqlStringUtils {

  public static @Nullable String fieldToString(final @Nullable MergedField field) {
    if (field == null) {
      return null;
    }

    return field.getName();
  }

  public static @Nullable String typeToString(final @Nullable GraphQLOutputType type) {
    if (type == null) {
      return null;
    }

    if (type instanceof GraphQLNamedOutputType) {
      final @NotNull GraphQLNamedOutputType namedType = (GraphQLNamedOutputType) type;
      return namedType.getName();
    }

    return StringUtils.toString(type);
  }

  public static @Nullable String objectTypeToString(final @Nullable GraphQLObjectType type) {
    if (type == null) {
      return null;
    }

    return type.getName();
  }
}

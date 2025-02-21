package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.protocol.MeasurementValue;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IOtelSpanWrapper extends ISpan {

  void setTransactionName(@NotNull String name);

  void setTransactionName(@NotNull String name, @NotNull TransactionNameSource nameSource);

  @ApiStatus.Internal
  @Nullable
  TransactionNameSource getTransactionNameSource();

  @ApiStatus.Internal
  @Nullable
  String getTransactionName();

  @NotNull
  SentryId getTraceId();

  @NotNull
  Map<String, Object> getData();

  @NotNull
  Map<String, MeasurementValue> getMeasurements();

  @Nullable
  Boolean isProfileSampled();

  @ApiStatus.Internal
  @NotNull
  IScopes getScopes();

  @ApiStatus.Internal
  @NotNull
  Map<String, String> getTags();

  @NotNull
  Context storeInContext(Context context);

  @ApiStatus.Internal
  @Nullable
  Attributes getOpenTelemetrySpanAttributes();
}

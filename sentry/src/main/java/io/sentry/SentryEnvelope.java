package io.sentry;

import io.sentry.exception.SentryEnvelopeException;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryEnvelope {

  // types: session_batch, session, event, attachment
  // an envelope cannot have 2 events, but eg multiple sessions
  private final @NotNull SentryEnvelopeHeader header;
  private final @NotNull Iterable<SentryEnvelopeItem> items;

  public @NotNull Iterable<SentryEnvelopeItem> getItems() {
    return items;
  }

  public @NotNull SentryEnvelopeHeader getHeader() {
    return header;
  }

  public SentryEnvelope(
      final @NotNull SentryEnvelopeHeader header,
      final @NotNull Iterable<SentryEnvelopeItem> items) {
    this.header = Objects.requireNonNull(header, "SentryEnvelopeHeader is required.");
    this.items = Objects.requireNonNull(items, "SentryEnvelope items are required.");
  }

  public SentryEnvelope(
      final @Nullable SentryId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @NotNull Iterable<SentryEnvelopeItem> items) {
    header = new SentryEnvelopeHeader(eventId, sdkVersion);
    this.items = Objects.requireNonNull(items, "SentryEnvelope items are required.");
  }

  public SentryEnvelope(
      final @Nullable SentryId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @NotNull SentryEnvelopeItem item) {
    Objects.requireNonNull(item, "SentryEnvelopeItem is required.");

    header = new SentryEnvelopeHeader(eventId, sdkVersion);
    final List<SentryEnvelopeItem> items = new ArrayList<>(1);
    items.add(item);
    this.items = items;
  }

  public static @NotNull SentryEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull Session session,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(session, "session is required.");

    return new SentryEnvelope(
        null, sdkVersion, SentryEnvelopeItem.fromSession(serializer, session));
  }

  public static @NotNull SentryEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull SentryBaseEvent event,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(event, "item is required.");

    return new SentryEnvelope(
        event.getEventId(), sdkVersion, SentryEnvelopeItem.fromEvent(serializer, event));
  }

  public static @NotNull SentryEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull ProfilingTraceData profilingTraceData,
      final long maxTraceFileSize,
      final @Nullable SdkVersion sdkVersion)
      throws SentryEnvelopeException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(profilingTraceData, "Profiling trace data is required.");

    return new SentryEnvelope(
        new SentryId(profilingTraceData.getProfileId()),
        sdkVersion,
        SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, maxTraceFileSize, serializer));
  }
}

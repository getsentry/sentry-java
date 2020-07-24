package io.sentry.core;

import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Objects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
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

  public static @NotNull SentryEnvelope fromSession(
      final @NotNull ISerializer serializer,
      final @NotNull Session session,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(session, "session is required.");

    return new SentryEnvelope(
        null, sdkVersion, SentryEnvelopeItem.fromSession(serializer, session));
  }

  public static @NotNull SentryEnvelope fromEvent(
      final @NotNull ISerializer serializer,
      final @NotNull SentryEvent event,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(event, "Event is required.");

    return new SentryEnvelope(
        event.getEventId(), sdkVersion, SentryEnvelopeItem.fromEvent(serializer, event));
  }
}

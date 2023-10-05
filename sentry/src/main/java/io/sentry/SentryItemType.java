package io.sentry;

import io.sentry.clientreport.ClientReport;
import io.sentry.protocol.SentryTransaction;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public enum SentryItemType implements JsonSerializable {
  Session("session"),
  Event("event"), // DataCategory.Error
  UserFeedback("user_report"), // Sentry backend still uses user_report
  Attachment("attachment"),
  Transaction("transaction"),
  Profile("profile"),
  ClientReport("client_report"),
  ReplayEvent("replay_event"),
  ReplayRecording("replay_recording"),
  CheckIn("check_in"),
  Unknown("__unknown__"); // DataCategory.Unknown

  private final String itemType;

  public static SentryItemType resolve(Object item) {
    if (item instanceof SentryEvent) {
      return Event;
    } else if (item instanceof SentryTransaction) {
      return Transaction;
    } else if (item instanceof Session) {
      return Session;
    } else if (item instanceof ClientReport) {
      return ClientReport;
    } else {
      return Attachment;
    }
  }

  SentryItemType(final String itemType) {
    this.itemType = itemType;
  }

  public String getItemType() {
    return itemType;
  }

  public static @NotNull SentryItemType valueOfLabel(String itemType) {
    for (SentryItemType sentryItemType : values()) {
      if (sentryItemType.itemType.equals(itemType)) {
        return sentryItemType;
      }
    }
    return Unknown;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(itemType);
  }

  static final class Deserializer implements JsonDeserializer<SentryItemType> {

    @Override
    public @NotNull SentryItemType deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return SentryItemType.valueOfLabel(reader.nextString().toLowerCase(Locale.ROOT));
    }
  }
}

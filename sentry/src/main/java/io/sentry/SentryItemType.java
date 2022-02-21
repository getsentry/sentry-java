package io.sentry;

import io.sentry.protocol.SentryTransaction;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum SentryItemType {
  Session("session"),
  Event("event"), // DataCategory.Error
  UserFeedback("user_report"), // Sentry backend still uses user_report
  Attachment("attachment"),
  Transaction("transaction"),
  Profile("profile"),
  Unknown("__unknown__"); // DataCategory.Unknown

  private final String itemType;

  public static SentryItemType resolve(Object item) {
    if (item instanceof SentryEvent) {
      return Event;
    } else if (item instanceof SentryTransaction) {
      return Transaction;
    } else if (item instanceof Session) {
      return Session;
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
}

package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum SentryItemType {
  Session("session"),
  Event("event"), // DataCategory.Error
  UserFeedback("user_report"), // Sentry backend still uses user_report
  Attachment("attachment"),
  Transaction("transaction"),
  Unknown("__unknown__"); // DataCategory.Unknown

  private final String itemType;

  public static SentryItemType resolve(Object obj) {
    if (obj instanceof SentryEvent) {
      return Event;
    } else if (obj instanceof Transaction) {
      return Transaction;
    } else if (obj instanceof Session) {
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

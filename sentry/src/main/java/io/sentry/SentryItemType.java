package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum SentryItemType {
  Session("session"),
  Event("event"), // DataCategory.Error
  User_Report("user_report"), // UserFeedback
  Attachment("attachment"),
  Transaction("transaction"),
  Unknown("__unknown__"); // DataCategory.Unknown

  private final String itemType;

  SentryItemType(final String itemType) {
    this.itemType = itemType;
  }

  public String getItemType() {
    return itemType;
  }
}

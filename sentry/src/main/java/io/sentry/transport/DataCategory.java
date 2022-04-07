package io.sentry.transport;

import org.jetbrains.annotations.NotNull;

public enum DataCategory {
  All("__all__"),
  Default("default"), // same as Error
  Error("error"),
  Session("session"),
  Attachment("attachment"),
  Transaction("transaction"),
  Security("security"),
  UserReport("user_report"),
  Unknown("unknown");

  private final String category;

  DataCategory(final @NotNull String category) {
    this.category = category;
  }

  public String getCategory() {
    return category;
  }
}

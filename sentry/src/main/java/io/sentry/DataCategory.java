package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public enum DataCategory {
  All("__all__"),
  Default("default"), // same as Error
  Error("error"),
  Session("session"),
  Attachment("attachment"),
  Monitor("monitor"),
  Profile("profile"),
  MetricBucket("metric_bucket"),
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

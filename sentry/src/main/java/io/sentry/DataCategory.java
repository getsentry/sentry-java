package io.sentry;

import org.jetbrains.annotations.NotNull;

public enum DataCategory {
  All("__all__"),
  Default("default"), // same as Error
  Error("error"),
  Feedback("feedback"),
  Session("session"),
  Attachment("attachment"),
  LogItem("log_item"),
  LogByte("log_byte"),
  Monitor("monitor"),
  Profile("profile"),
  ProfileChunkUi("profile_chunk_ui"),
  ProfileChunk("profile_chunk"),
  Transaction("transaction"),
  Replay("replay"),
  Span("span"),
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

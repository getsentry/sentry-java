package io.sentry.clientreport;

import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class ClientReportKey {
  private final @NotNull String reason;
  private final @NotNull String category;

  ClientReportKey(@NotNull String reason, @NotNull String category) {
    this.reason = reason;
    this.category = category;
  }

  public @NotNull String getReason() {
    return reason;
  }

  public @NotNull String getCategory() {
    return category;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientReportKey)) return false;
    ClientReportKey that = (ClientReportKey) o;
    return Objects.equals(getReason(), that.getReason())
        && Objects.equals(getCategory(), that.getCategory());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getReason(), getCategory());
  }
}

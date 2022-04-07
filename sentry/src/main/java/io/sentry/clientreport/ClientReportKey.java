package io.sentry.clientreport;

import java.util.Objects;

final class ClientReportKey {
  private final String reason;
  private final String category;

  ClientReportKey(String reason, String category) {
    this.reason = reason;
    this.category = category;
  }

  public String getReason() {
    return reason;
  }

  public String getCategory() {
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

  @Override
  public String toString() {
    return "ClientReportKey{" + "reason='" + reason + '\'' + ", category='" + category + '\'' + '}';
  }
}

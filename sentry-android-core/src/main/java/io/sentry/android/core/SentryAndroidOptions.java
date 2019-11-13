package io.sentry.android.core;

import io.sentry.core.SentryOptions;

public final class SentryAndroidOptions extends SentryOptions {
  private boolean anrEnabled = true;
  private int anrTimeoutIntervalMills = 4000;
  private boolean anrReportInDebug = false;

  public boolean isAnrEnabled() {
    return anrEnabled;
  }

  public void setAnrEnabled(boolean anrEnabled) {
    this.anrEnabled = anrEnabled;
  }

  public int getAnrTimeoutIntervalMills() {
    return anrTimeoutIntervalMills;
  }

  public void setAnrTimeoutIntervalMills(int anrTimeoutIntervalMills) {
    this.anrTimeoutIntervalMills = anrTimeoutIntervalMills;
  }

  public boolean isAnrReportInDebug() {
    return anrReportInDebug;
  }

  public void setAnrReportInDebug(boolean anrReportInDebug) {
    this.anrReportInDebug = anrReportInDebug;
  }
}

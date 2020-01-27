package io.sentry.android.core;

import io.sentry.core.SentryOptions;

/** Sentry SDK options for Android */
public final class SentryAndroidOptions extends SentryOptions {

  /**
   * Enable or disable ANR (Application Not Responding) Default is enabled Used by AnrIntegration
   */
  private boolean anrEnabled = true;

  /** ANR Timeout internal in Mills Default is 4000 = 4s Used by AnrIntegration */
  private long anrTimeoutIntervalMills = 4000;

  /** Enable or disable ANR on Debug mode Default is disabled Used by AnrIntegration */
  private boolean anrReportInDebug = false;

  /**
   * Checks if ANR (Application Not Responding) is enabled or disabled Default is enabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAnrEnabled() {
    return anrEnabled;
  }

  /**
   * Sets ANR (Application Not Responding) to enabled or disabled Default is enabled
   *
   * @param anrEnabled true for enabled and false for disabled
   */
  public void setAnrEnabled(boolean anrEnabled) {
    this.anrEnabled = anrEnabled;
  }

  /**
   * Returns the ANR timeout internal in Mills Default is 4000 = 4s
   *
   * @return the timeout in mills
   */
  public long getAnrTimeoutIntervalMills() {
    return anrTimeoutIntervalMills;
  }

  /**
   * Sets the ANR timeout internal in Mills Default is 4000 = 4s
   *
   * @param anrTimeoutIntervalMills the timeout internal in Mills
   */
  public void setAnrTimeoutIntervalMills(long anrTimeoutIntervalMills) {
    this.anrTimeoutIntervalMills = anrTimeoutIntervalMills;
  }

  /**
   * Checks if ANR (Application Not Responding) is enabled or disabled on Debug mode Default is
   * disabled
   *
   * @return true if enabled or false otherwise
   */
  public boolean isAnrReportInDebug() {
    return anrReportInDebug;
  }

  /**
   * Sets ANR (Application Not Responding) to enabled or disabled on Debug mode Default is disabled
   *
   * @param anrReportInDebug true for enabled and false for disabled
   */
  public void setAnrReportInDebug(boolean anrReportInDebug) {
    this.anrReportInDebug = anrReportInDebug;
  }
}

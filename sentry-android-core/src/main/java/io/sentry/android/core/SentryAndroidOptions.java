package io.sentry.android.core;

import io.sentry.core.SentryOptions;

/** Sentry SDK options for Android */
public final class SentryAndroidOptions extends SentryOptions {

  /**
   * Enable or disable ANR (Application Not Responding) Default is enabled Used by AnrIntegration
   */
  private boolean anrEnabled = true;

  /** ANR Timeout internal in Millis Default is 4000 = 4s Used by AnrIntegration */
  private long anrTimeoutIntervalMillis = 4000;

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
   * @deprecated use {@link #getAnrTimeoutIntervalMillis , #getAnrTimeoutIntervalMillis} instead.
   */
  @Deprecated
  public long getAnrTimeoutIntervalMills() {
    return getAnrTimeoutIntervalMillis();
  }

  /**
   * @deprecated use {@link #setAnrTimeoutIntervalMillis , #setAnrTimeoutIntervalMillis} instead.
   */
  @Deprecated
  public void setAnrTimeoutIntervalMills(long anrTimeoutIntervalMillis) {
    setAnrTimeoutIntervalMillis(anrTimeoutIntervalMillis);
  }

  /**
   * Returns the ANR timeout internal in Millis Default is 4000 = 4s
   *
   * @return the timeout in millis
   */
  public long getAnrTimeoutIntervalMillis() {
    return anrTimeoutIntervalMillis;
  }

  /**
   * Sets the ANR timeout internal in Millis Default is 4000 = 4s
   *
   * @param anrTimeoutIntervalMillis the timeout internal in Millis
   */
  public void setAnrTimeoutIntervalMillis(long anrTimeoutIntervalMillis) {
    this.anrTimeoutIntervalMillis = anrTimeoutIntervalMillis;
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

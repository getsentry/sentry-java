package io.sentry.android.core.anr;

public class AnrException extends Exception {

  private static final long serialVersionUID = -4634908272864849327L;

  public AnrException(String message) {
    super(message);
  }
}

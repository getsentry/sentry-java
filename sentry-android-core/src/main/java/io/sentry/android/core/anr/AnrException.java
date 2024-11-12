package io.sentry.android.core.anr;

public class AnrException extends Exception {

  private static final long serialVersionUID = 2301073572127052005L;

  public AnrException() {}

  public AnrException(String message) {
    super(message);
  }
}

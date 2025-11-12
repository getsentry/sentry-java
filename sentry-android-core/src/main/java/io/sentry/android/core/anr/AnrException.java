package io.sentry.android.core.anr;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class AnrException extends Exception {

  private static final long serialVersionUID = 8615243433409006646L;

  public AnrException() {}

  public AnrException(String message) {
    super(message);
  }
}

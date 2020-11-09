package io.sentry.transport;

import java.net.Authenticator;
import org.jetbrains.annotations.NotNull;

/** Wraps {@link Authenticator} in order to make classes that use it testable. */
final class AuthenticatorWrapper {
  private static final AuthenticatorWrapper instance = new AuthenticatorWrapper();

  public static AuthenticatorWrapper getInstance() {
    return instance;
  }

  private AuthenticatorWrapper() {}

  public void setDefault(final @NotNull Authenticator authenticator) {
    Authenticator.setDefault(authenticator);
  }
}

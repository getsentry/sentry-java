package io.sentry.transport;

import io.sentry.util.Objects;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProxyAuthenticator extends Authenticator {
  private final @NotNull String user;
  private final @NotNull String password;

  /**
   * Proxy authenticator.
   *
   * @param user proxy username
   * @param password proxy password
   */
  ProxyAuthenticator(final @NotNull String user, final @NotNull String password) {
    this.user = Objects.requireNonNull(user, "user is required");
    this.password = Objects.requireNonNull(password, "password is required");
  }

  @Override
  protected @Nullable PasswordAuthentication getPasswordAuthentication() {
    if (getRequestorType() == RequestorType.PROXY) {
      return new PasswordAuthentication(user, password.toCharArray());
    }
    return null;
  }

  @NotNull
  String getUser() {
    return user;
  }

  @NotNull
  String getPassword() {
    return password;
  }
}

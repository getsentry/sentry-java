package io.sentry;

import org.jetbrains.annotations.NotNull;

/**
 * Code that provides middlewares, bindings or hooks into certain frameworks or environments, along
 * with code that inserts those bindings and activates them.
 */
public interface Integration {
  /**
   * Registers an integration
   *
   * @param scopes the Scopes
   * @param options the options
   */
  void register(@NotNull IScopes scopes, @NotNull SentryOptions options);
}

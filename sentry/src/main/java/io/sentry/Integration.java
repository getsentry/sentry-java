package io.sentry;

import org.jetbrains.annotations.NotNull;

/**
 * Code that provides middlewares, bindings or hooks into certain frameworks or environments, along
 * with code that inserts those bindings and activates them.
 */
public interface Integration extends IntegrationName {
  /**
   * Registers an integration
   *
   * @param hub the Hub
   * @param options the options
   */
  void register(@NotNull IHub hub, @NotNull SentryOptions options);
}

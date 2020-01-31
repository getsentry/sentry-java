package io.sentry.core;

/**
 * Code that provides middlewares, bindings or hooks into certain frameworks or environments, along
 * with code that inserts those bindings and activates them.
 */
public interface Integration {
  /**
   * Registers an integration
   *
   * @param hub the Hub
   * @param options the options
   */
  void register(IHub hub, SentryOptions options);
}

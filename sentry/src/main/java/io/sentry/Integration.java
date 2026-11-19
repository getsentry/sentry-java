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
   * <p>If this method throws, {@link Sentry#init} skips the integration and continues with the next
   * one, so the SDK starts without what this integration would have set up. Fatal errors such as
   * {@link OutOfMemoryError} are rethrown instead. The skipped integration is only reported as a
   * warning through {@link SentryOptions#getLogger()}, which by default prints only when {@link
   * SentryOptions#isDebug()} is enabled. To find out whether your integration registered, enable
   * debug and look for "Failed to register the integration" in the log.
   *
   * @param scopes the Scopes
   * @param options the options
   */
  void register(@NotNull IScopes scopes, @NotNull SentryOptions options);
}

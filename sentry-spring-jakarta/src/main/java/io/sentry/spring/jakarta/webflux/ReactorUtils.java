package io.sentry.spring.jakarta.webflux;

import io.sentry.IScopes;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

// TODO deprecate and replace with "withSentryScopes" etc.
@ApiStatus.Experimental
// TODO [HSM] do we keep old methods around and deprecate them?
// TODO [HSM] do we need to offer isolated variants?
public final class ReactorUtils {

  /**
   * Writes the current Sentry {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withSentry(final @NotNull Mono<T> mono) {
    final @NotNull IScopes oldScopes = Sentry.getCurrentScopes();
    final @NotNull IScopes forkedScopes = oldScopes.forkedCurrentScope("reactor.withSentry");
    return withSentryScopes(mono, forkedScopes);
  }

  /**
   * Writes a new Sentry {@link IScopes} cloned from the main scopes to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withSentryForkedRoots(final @NotNull Mono<T> mono) {
    final @NotNull IScopes scopes = Sentry.forkedRootScopes("reactor");
    return withSentryScopes(mono, scopes);
  }

  /**
   * Writes the given Sentry {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withSentryScopes(
      final @NotNull Mono<T> mono, final @NotNull IScopes scopes) {
    /**
     * WARNING: Cannot set the scopes as current. It would be used by others to clone again causing
     * shared scopes and thus leading to issues like unrelated breadcrumbs showing up in events.
     */
    // Sentry.setCurrentScopes(forkedScopes);

    return Mono.deferContextual(ctx -> mono)
        .contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, scopes));
  }

  /**
   * Writes the current Sentry {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withSentry(final @NotNull Flux<T> flux) {
    final @NotNull IScopes oldScopes = Sentry.getCurrentScopes();
    final @NotNull IScopes forkedScopes = oldScopes.forkedCurrentScope("reactor.withSentry");

    return withSentryScopes(flux, forkedScopes);
  }

  /**
   * Writes a new Sentry {@link IScopes} cloned from the main scopes to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withSentryForkedRoots(final @NotNull Flux<T> flux) {
    final @NotNull IScopes scopes = Sentry.forkedRootScopes("reactor");
    return withSentryScopes(flux, scopes);
  }

  /**
   * Writes the given Sentry {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withSentryScopes(
      final @NotNull Flux<T> flux, final @NotNull IScopes scopes) {
    /**
     * WARNING: Cannot set the scopes as current. It would be used by others to clone again causing
     * shared scopes and thus leading to issues like unrelated breadcrumbs showing up in events.
     */
    // Sentry.setCurrentScopes(forkedScopes);

    return Flux.deferContextual(ctx -> flux)
        .contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, scopes));
  }
}

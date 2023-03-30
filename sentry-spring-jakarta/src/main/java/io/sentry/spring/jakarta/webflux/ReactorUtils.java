package io.sentry.spring.jakarta.webflux;

import io.sentry.IHub;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@ApiStatus.Experimental
public final class ReactorUtils {

  /**
   * Writes the current Sentry {@link IHub} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Mono<T> withSentry(final @NotNull Mono<T> mono) {
    final @NotNull IHub oldHub = Sentry.getCurrentHub();
    final @NotNull IHub clonedHub = oldHub.clone();
    return withSentryHub(mono, clonedHub);
  }

  /**
   * Writes a new Sentry {@link IHub} cloned from the main hub to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Mono<T> withSentryNewMainHubClone(final @NotNull Mono<T> mono) {
    final @NotNull IHub hub = Sentry.cloneMainHub();
    return withSentryHub(mono, hub);
  }

  /**
   * Writes the given Sentry {@link IHub} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Mono<T> withSentryHub(final @NotNull Mono<T> mono, final @NotNull IHub hub) {
    /**
     * WARNING: Cannot set the hub as current. It would be used by others to clone again causing
     * shared hubs and scopes and thus leading to issues like unrelated breadcrumbs showing up in
     * events.
     */
    // Sentry.setCurrentHub(clonedHub);

    return Mono.deferContextual(ctx -> mono)
        .contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, hub));
  }

  /**
   * Writes the current Sentry {@link IHub} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Flux<T> withSentry(final @NotNull Flux<T> flux) {
    final @NotNull IHub oldHub = Sentry.getCurrentHub();
    final @NotNull IHub clonedHub = oldHub.clone();

    return withSentryHub(flux, clonedHub);
  }

  /**
   * Writes a new Sentry {@link IHub} cloned from the main hub to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Flux<T> withSentryNewMainHubClone(final @NotNull Flux<T> flux) {
    final @NotNull IHub hub = Sentry.cloneMainHub();
    return withSentryHub(flux, hub);
  }

  /**
   * Writes the given Sentry {@link IHub} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  @ApiStatus.Experimental
  public static <T> Flux<T> withSentryHub(final @NotNull Flux<T> flux, final @NotNull IHub hub) {
    /**
     * WARNING: Cannot set the hub as current. It would be used by others to clone again causing
     * shared hubs and scopes and thus leading to issues like unrelated breadcrumbs showing up in
     * events.
     */
    // Sentry.setCurrentHub(clonedHub);

    return Flux.deferContextual(ctx -> flux)
        .contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, hub));
  }
}

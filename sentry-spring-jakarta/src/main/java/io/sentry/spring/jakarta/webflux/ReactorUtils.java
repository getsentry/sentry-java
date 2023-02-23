package io.sentry.spring.jakarta.webflux;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import io.sentry.IHub;
import io.sentry.Sentry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@ApiStatus.Experimental
public final class ReactorUtils {

  /**
   * Writes the Sentry {@link IHub} to the {@link Context} and uses {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * This requires
   *  - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be enabled
   *  - having `io.micrometer:context-propagation:1.0.2` or newer as dependency
   *  - having `io.projectreactor:reactor-core:3.5.3` or newer as dependency
   */
  @ApiStatus.Experimental
  public static <T> Mono<T> withSentry(Mono<T> mono) {
    final @NotNull IHub oldHub = Sentry.getCurrentHub();
    final @NotNull IHub clonedHub = oldHub.clone();

    /**
     * WARNING: Cannot set the clonedHub as current hub.
     * It would be used by others to clone again causing shared hubs and scopes and thus
     * leading to issues like unrelated breadcrumbs showing up in events.
     */
    // Sentry.setCurrentHub(clonedHub);

    return Mono.deferContextual(ctx -> mono).contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, clonedHub));
  }

  /**
   * Writes the Sentry {@link IHub} to the {@link Context} and uses {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * This requires
   *  - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be enabled
   *  - having `io.micrometer:context-propagation:1.0.2` or newer as dependency
   *  - having `io.projectreactor:reactor-core:3.5.3` or newer as dependency
   */
  @ApiStatus.Experimental
  public static <T> Flux<T> withSentry(Flux<T> flux) {
    final @NotNull IHub oldHub = Sentry.getCurrentHub();
    final @NotNull IHub clonedHub = oldHub.clone();

    /**
     * WARNING: Cannot set the clonedHub as current hub.
     * It would be used by others to clone again causing shared hubs and scopes and thus
     * leading to issues like unrelated breadcrumbs showing up in events.
     */
    // Sentry.setCurrentHub(clonedHub);

    return Flux.deferContextual(ctx -> flux).contextWrite(Context.of(SentryReactorThreadLocalAccessor.KEY, clonedHub));
  }
}

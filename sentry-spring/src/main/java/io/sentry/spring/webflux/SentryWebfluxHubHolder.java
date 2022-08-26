package io.sentry.spring.webflux;

import static io.sentry.spring.webflux.SentryWebFilter.SENTRY_HUB_KEY;

import io.sentry.IHub;
import io.sentry.Sentry;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

public final class SentryWebfluxHubHolder {

  public static @Nullable IHub getHubFromAttributes(
      final @NotNull ServerWebExchange serverWebExchange) {
    @Nullable
    Object hubFromAttributesObject = serverWebExchange.getAttributes().get(SENTRY_HUB_KEY);
    return hubFromAttributesObject == null ? null : (IHub) hubFromAttributesObject;
  }

  public static @NotNull Mono<IHub> getHub() {
    return Mono.deferContextual(
        ctx -> {
          @Nullable final Object hubObject = ctx.get(SENTRY_HUB_KEY);
          if (hubObject == null) {
            return Mono.error(
                new RuntimeException("Unable to find sentry hub in reactor context."));
          }
          return Mono.just((IHub) hubObject);
        });
  }

  public static @NotNull Flux<IHub> getHubFlux() {
    return getHub().flux();
  }

  public static @NotNull Mono<IHub> getHub(final @NotNull ServerWebExchange serverWebExchange) {
    return Mono.deferContextual(
        ctx -> {
          @NotNull final Optional<IHub> hub = ctx.getOrEmpty(SENTRY_HUB_KEY);
          if (hub.isPresent()) {
            return Mono.just(hub.get());
          } else {
            @Nullable final IHub hubFromAttributes = getHubFromAttributes(serverWebExchange);
            if (hubFromAttributes == null) {
              return Mono.error(
                  new RuntimeException(
                      "Unable to find sentry hub in reactor context or attributes."));
            } else {
              return Mono.just(hubFromAttributes);
            }
          }
        });
  }

  public static @NotNull Runnable withSentryOnFirst(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Runnable runnable) {
    return () -> {
      @Nullable final IHub hub = getHubFromAttributes(serverWebExchange);
      if (hub != null) {
        Sentry.setCurrentHub(hub);
        runnable.run();
      } else {
        runnable.run();
      }
    };
  }

  public static @NotNull Runnable withSentryOnComplete(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Runnable runnable) {
    return withSentryOnFirst(serverWebExchange, runnable);
  }

  public static @NotNull Consumer<SignalType> withSentryFinally(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull Consumer<SignalType> consumer) {
    return signalType -> {
      @Nullable final IHub hub = getHubFromAttributes(serverWebExchange);
      if (hub != null) {
        Sentry.setCurrentHub(hub);
        // TODO does resetting hub in finally make a difference?
        consumer.accept(signalType);
      } else {
        consumer.accept(signalType);
      }
    };
  }

  public static @NotNull <T> Consumer<T> withSentryOnNext(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Consumer<T> consumer) {
    return param -> {
      @Nullable final IHub hub = getHubFromAttributes(serverWebExchange);
      if (hub != null) {
        Sentry.setCurrentHub(hub);
        // TODO does resetting hub in finally make a difference?
        consumer.accept(param);
      } else {
        consumer.accept(param);
      }
    };
  }

  public static @NotNull Consumer<? super Throwable> withSentryOnError(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull Consumer<? super Throwable> consumer) {
    return t -> {
      //      if (SignalType.ON_COMPLETE.equals(signalType)) {
      //        return;
      //      }
      @Nullable final IHub hub = getHubFromAttributes(serverWebExchange);
      if (hub != null) {
        Sentry.setCurrentHub(hub);
        // TODO does resetting hub in finally make a difference?
        consumer.accept(t);
      } else {
        consumer.accept(t);
      }
    };
  }

  public static <T> @NotNull Consumer<Signal<T>> withSentryOnNext(
      final @NotNull Consumer<T> consumer) {
    return signal -> {
      if (!signal.isOnNext()) {
        return;
      }

      Optional<IHub> hub = signal.getContextView().getOrEmpty(SENTRY_HUB_KEY);
      if (hub.isPresent()) {
        Sentry.setCurrentHub(hub.get());
        // TODO does resetting hub in finally make a difference?
        consumer.accept(signal.get());
      } else {
        consumer.accept(signal.get());
      }
    };
  }

  public static <T> @NotNull Consumer<Signal<T>> withSentryOnComplete(
      final @NotNull Consumer<T> consumer) {
    return signal -> {
      if (!signal.isOnComplete()) {
        return;
      }

      Optional<IHub> hub = signal.getContextView().getOrEmpty(SENTRY_HUB_KEY);
      if (hub.isPresent()) {
        Sentry.setCurrentHub(hub.get());
        // TODO does resetting hub in finally make a difference?
        consumer.accept(signal.get());
      } else {
        consumer.accept(signal.get());
      }
    };
  }

  // TODO not working?
  public static <T> @NotNull Consumer<Signal<T>> withSentryOnSubscribe(
      final @NotNull Consumer<T> consumer) {
    return signal -> {
      if (!signal.isOnSubscribe()) {
        return;
      }

      Optional<IHub> hub = signal.getContextView().getOrEmpty(SENTRY_HUB_KEY);
      if (hub.isPresent()) {
        Sentry.setCurrentHub(hub.get());
        // TODO does resetting hub in finally make a difference?
        consumer.accept(signal.get());
      } else {
        consumer.accept(signal.get());
      }
    };
  }

  public static <T> @NotNull Consumer<Signal<T>> withSentryOnError(
      final @NotNull Consumer<? super Throwable> consumer) {
    return signal -> {
      if (!signal.isOnError()) {
        return;
      }
      Optional<IHub> hub = signal.getContextView().getOrEmpty(SENTRY_HUB_KEY);
      if (hub.isPresent()) {
        Sentry.setCurrentHub(hub.get());
        // TODO does resetting hub in finally make a difference?
        consumer.accept(signal.getThrowable());
      } else {
        consumer.accept(signal.getThrowable());
      }
    };
  }
}

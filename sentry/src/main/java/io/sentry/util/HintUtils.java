package io.sentry.util;

import static io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT;

import io.sentry.ILogger;
import io.sentry.hints.ApplyScopeData;
import io.sentry.hints.Cached;
import io.sentry.hints.Hints;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Util class for Applying or not scope's data to an event */
@ApiStatus.Internal
public final class HintUtils {

  private HintUtils() {}

  @ApiStatus.Internal
  public static Hints createWithTypeCheckHint(Object hint) {
    Hints hints = new Hints();
    setTypeCheckHint(hints, hint);
    return hints;
  }

  @ApiStatus.Internal
  public static void setTypeCheckHint(@NotNull Hints hints, Object hint) {
    hints.set(SENTRY_TYPE_CHECK_HINT, hint);
  }

  @ApiStatus.Internal
  public static @Nullable Object getSentrySdkHint(@NotNull Hints hints) {
    return hints.get(SENTRY_TYPE_CHECK_HINT);
  }

  @ApiStatus.Internal
  public static boolean hasType(@NotNull Hints hints, @NotNull Class<?> clazz) {
    final Object sentrySdkHint = getSentrySdkHint(hints);
    return clazz.isInstance(sentrySdkHint);
  }

  @ApiStatus.Internal
  public static <T> void runIfDoesNotHaveType(
      @NotNull Hints hints, @NotNull Class<T> clazz, SentryNullableConsumer<Object> lambda) {
    runIfHasType(
        hints,
        clazz,
        (ignored) -> {},
        (hint, clazz2) -> {
          lambda.accept(hint);
        });
  }

  @ApiStatus.Internal
  public static <T> void runIfHasType(
      @NotNull Hints hints, @NotNull Class<T> clazz, SentryConsumer<T> lambda) {
    runIfHasType(hints, clazz, lambda, (hint, clazz2) -> {});
  }

  @ApiStatus.Internal
  public static <T> void runIfHasTypeLogIfNot(
      @NotNull Hints hints, @NotNull Class<T> clazz, ILogger logger, SentryConsumer<T> lambda) {
    runIfHasType(
        hints,
        clazz,
        lambda,
        (sentrySdkHint, expectedClass) -> {
          LogUtils.logNotInstanceOf(expectedClass, sentrySdkHint, logger);
        });
  }

  @SuppressWarnings("unchecked")
  @ApiStatus.Internal
  public static <T> void runIfHasType(
      @NotNull Hints hints,
      @NotNull Class<T> clazz,
      SentryConsumer<T> lambda,
      SentryFallbackConsumer fallbackLambda) {
    Object sentrySdkHint = getSentrySdkHint(hints);
    if (hasType(hints, clazz) && sentrySdkHint != null) {
      lambda.accept((T) sentrySdkHint);
    } else {
      fallbackLambda.accept(sentrySdkHint, clazz);
    }
  }

  /**
   * Scope's data should be applied if: Hint is of the type ApplyScopeData or Hint is not Cached
   * (this includes a null hint)
   *
   * @return true if it should apply scope's data or false otherwise
   */
  @ApiStatus.Internal
  public static boolean shouldApplyScopeData(@NotNull Hints hints) {
    return !hasType(hints, Cached.class) || hasType(hints, ApplyScopeData.class);
  }

  @FunctionalInterface
  public interface SentryConsumer<T> {
    void accept(@NotNull T t);
  }

  @FunctionalInterface
  public interface SentryNullableConsumer<T> {
    void accept(@Nullable T t);
  }

  @FunctionalInterface
  public interface SentryFallbackConsumer {
    void accept(@Nullable Object sentrySdkHint, @NotNull Class<?> clazz);
  }
}

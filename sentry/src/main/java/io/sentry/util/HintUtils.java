package io.sentry.util;

import static io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT;

import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.hints.ApplyScopeData;
import io.sentry.hints.Cached;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Util class dealing with Hint as not to pollute the Hint API with internal functionality */
@ApiStatus.Internal
public final class HintUtils {

  private HintUtils() {}

  @ApiStatus.Internal
  public static Hint createWithTypeCheckHint(Object typeCheckHint) {
    Hint hint = new Hint();
    setTypeCheckHint(hint, typeCheckHint);
    return hint;
  }

  @ApiStatus.Internal
  public static void setTypeCheckHint(@NotNull Hint hint, Object typeCheckHint) {
    hint.set(SENTRY_TYPE_CHECK_HINT, typeCheckHint);
  }

  @ApiStatus.Internal
  public static @Nullable Object getSentrySdkHint(@NotNull Hint hint) {
    return hint.get(SENTRY_TYPE_CHECK_HINT);
  }

  @ApiStatus.Internal
  public static boolean hasType(@NotNull Hint hint, @NotNull Class<?> clazz) {
    final Object sentrySdkHint = getSentrySdkHint(hint);
    return clazz.isInstance(sentrySdkHint);
  }

  @ApiStatus.Internal
  public static <T> void runIfDoesNotHaveType(
      @NotNull Hint hint, @NotNull Class<T> clazz, SentryNullableConsumer<Object> lambda) {
    runIfHasType(
        hint,
        clazz,
        (ignored) -> {},
        (value, clazz2) -> {
          lambda.accept(value);
        });
  }

  @ApiStatus.Internal
  public static <T> void runIfHasType(
      @NotNull Hint hint, @NotNull Class<T> clazz, SentryConsumer<T> lambda) {
    runIfHasType(hint, clazz, lambda, (value, clazz2) -> {});
  }

  @ApiStatus.Internal
  public static <T> void runIfHasTypeLogIfNot(
      @NotNull Hint hint, @NotNull Class<T> clazz, ILogger logger, SentryConsumer<T> lambda) {
    runIfHasType(
        hint,
        clazz,
        lambda,
        (sentrySdkHint, expectedClass) -> {
          LogUtils.logNotInstanceOf(expectedClass, sentrySdkHint, logger);
        });
  }

  @SuppressWarnings("unchecked")
  @ApiStatus.Internal
  public static <T> void runIfHasType(
      @NotNull Hint hint,
      @NotNull Class<T> clazz,
      SentryConsumer<T> lambda,
      SentryHintFallback fallbackLambda) {
    Object sentrySdkHint = getSentrySdkHint(hint);
    if (hasType(hint, clazz) && sentrySdkHint != null) {
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
  public static boolean shouldApplyScopeData(@NotNull Hint hint) {
    return !hasType(hint, Cached.class) || hasType(hint, ApplyScopeData.class);
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
  public interface SentryHintFallback {
    void accept(@Nullable Object sentrySdkHint, @NotNull Class<?> clazz);
  }
}

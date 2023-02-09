package io.sentry.util;

import static io.sentry.TypeCheckHint.SENTRY_DART_SDK_NAME;
import static io.sentry.TypeCheckHint.SENTRY_DOTNET_SDK_NAME;
import static io.sentry.TypeCheckHint.SENTRY_IS_FROM_HYBRID_SDK;
import static io.sentry.TypeCheckHint.SENTRY_JAVASCRIPT_SDK_NAME;
import static io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT;

import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.hints.ApplyScopeData;
import io.sentry.hints.Backfillable;
import io.sentry.hints.Cached;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Util class dealing with Hint as not to pollute the Hint API with internal functionality */
@ApiStatus.Internal
public final class HintUtils {

  private HintUtils() {}

  public static void setIsFromHybridSdk(final @NotNull Hint hint, final @NotNull String sdkName) {
    if (sdkName.startsWith(SENTRY_JAVASCRIPT_SDK_NAME)
        || sdkName.startsWith(SENTRY_DART_SDK_NAME)
        || sdkName.startsWith(SENTRY_DOTNET_SDK_NAME)) {
      hint.set(SENTRY_IS_FROM_HYBRID_SDK, true);
    }
  }

  public static boolean isFromHybridSdk(final @NotNull Hint hint) {
    return Boolean.TRUE.equals(hint.getAs(SENTRY_IS_FROM_HYBRID_SDK, Boolean.class));
  }

  public static Hint createWithTypeCheckHint(Object typeCheckHint) {
    Hint hint = new Hint();
    setTypeCheckHint(hint, typeCheckHint);
    return hint;
  }

  public static void setTypeCheckHint(@NotNull Hint hint, Object typeCheckHint) {
    hint.set(SENTRY_TYPE_CHECK_HINT, typeCheckHint);
  }

  public static @Nullable Object getSentrySdkHint(@NotNull Hint hint) {
    return hint.get(SENTRY_TYPE_CHECK_HINT);
  }

  public static boolean hasType(@NotNull Hint hint, @NotNull Class<?> clazz) {
    final Object sentrySdkHint = getSentrySdkHint(hint);
    return clazz.isInstance(sentrySdkHint);
  }

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

  public static <T> void runIfHasType(
      @NotNull Hint hint, @NotNull Class<T> clazz, SentryConsumer<T> lambda) {
    runIfHasType(hint, clazz, lambda, (value, clazz2) -> {});
  }

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
  public static boolean shouldApplyScopeData(@NotNull Hint hint) {
    return (!hasType(hint, Cached.class) && !hasType(hint, Backfillable.class)) || hasType(hint, ApplyScopeData.class);
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

package io.sentry.spring.checkin;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.MonitorConfig;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.time.Stopwatch;
import io.sentry.util.MonitorConfigUtils;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringValueResolver;

/**
 * Reports execution of every bean method annotated with {@link SentryCheckIn} as a monitor
 * check-in.
 */
@ApiStatus.Internal
@Open
public class SentryCheckInAdvice implements MethodInterceptor, EmbeddedValueResolverAware {
  private final @NotNull IScopes scopes;

  private @Nullable StringValueResolver resolver;

  public SentryCheckInAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public SentryCheckInAdvice(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    @Nullable
    SentryCheckIn checkInAnnotation =
        AnnotationUtils.findAnnotation(mostSpecificMethod, SentryCheckIn.class);
    if (checkInAnnotation == null) {
      return invocation.proceed();
    }

    final boolean isHeartbeatOnly = checkInAnnotation.heartbeat();

    @Nullable String monitorSlug = checkInAnnotation.value();

    if (resolver != null) {
      try {
        monitorSlug = resolver.resolveStringValue(checkInAnnotation.value());
      } catch (Throwable e) {
        // When resolving fails, we fall back to the original string which may contain unresolved
        // expressions.
        // Testing shows this can also happen if properties cannot be resolved (without an exception
        // being thrown).
        // Sentry should alert the user about missed checkins in this case since the monitor slug
        // won't match
        // what is configured in Sentry.
        scopes
            .getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Slug for method annotated with @SentryCheckIn could not be resolved from properties.",
                e);
      }
    }

    if (ObjectUtils.isEmpty(monitorSlug)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Not capturing check-in for method annotated with @SentryCheckIn because it does not specify a monitor slug.");
      return invocation.proceed();
    }

    try (final @NotNull ISentryLifecycleToken ignored =
            scopes.forkedScopes("SentryCheckInAdvice").makeCurrent()) {
      TracingUtils.startNewTrace(scopes);

      @Nullable SentryId checkInId = null;
      final @NotNull Stopwatch stopwatch =
          Stopwatch.started(scopes.getOptions().getMonotonicTicker());
      boolean didError = false;

      try {
        if (!isHeartbeatOnly) {
          final @NotNull CheckIn inProgress = new CheckIn(monitorSlug, CheckInStatus.IN_PROGRESS);
          if (checkInAnnotation.upsertMonitorConfig()) {
            inProgress.setMonitorConfig(monitorConfigFromScheduled(mostSpecificMethod));
          }
          checkInId = scopes.captureCheckIn(inProgress);
        }
        return invocation.proceed();
      } catch (Throwable e) {
        didError = true;
        throw e;
      } finally {
        final @NotNull CheckInStatus status = didError ? CheckInStatus.ERROR : CheckInStatus.OK;
        CheckIn checkIn = new CheckIn(checkInId, monitorSlug, status);
        checkIn.setDuration(DateUtils.nanosToSeconds(stopwatch.elapsedNanos()));
        scopes.captureCheckIn(checkIn);
      }
    }
  }

  private @Nullable MonitorConfig monitorConfigFromScheduled(final @NotNull Method method) {
    try {
      final @NotNull Set<Scheduled> schedules =
          AnnotatedElementUtils.findMergedRepeatableAnnotations(method, Scheduled.class);
      if (schedules.size() != 1) {
        return null;
      }
      final @NotNull Scheduled scheduled = schedules.iterator().next();
      // timeUnit only exists from Spring 5.3.10, so read it as an attribute
      final @Nullable Object timeUnitAttribute =
          AnnotationUtils.getAnnotationAttributes(scheduled).get("timeUnit");
      final @NotNull TimeUnit timeUnit =
          timeUnitAttribute instanceof TimeUnit
              ? (TimeUnit) timeUnitAttribute
              : TimeUnit.MILLISECONDS;
      final @Nullable String cron = resolve(scheduled.cron());
      final @Nullable String zone =
          cron != null && !cron.isEmpty() ? resolve(scheduled.zone()) : null;
      return MonitorConfigUtils.fromSchedule(
          cron,
          zone,
          periodMillis(scheduled.fixedRate(), scheduled.fixedRateString(), timeUnit),
          periodMillis(scheduled.fixedDelay(), scheduled.fixedDelayString(), timeUnit));
    } catch (RuntimeException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Could not derive a monitor config from @Scheduled for method annotated with @SentryCheckIn.",
              e);
      return null;
    }
  }

  private @Nullable Long periodMillis(
      final long value, final @NotNull String valueString, final @NotNull TimeUnit timeUnit) {
    if (value >= 0) {
      return timeUnit.toMillis(value);
    }
    final @Nullable String resolved = resolve(valueString);
    if (resolved == null || resolved.isEmpty()) {
      return null;
    }
    final @NotNull String trimmed = resolved.trim();
    if (trimmed.startsWith("P") || trimmed.startsWith("p")) {
      return Duration.parse(trimmed).toMillis();
    }
    return timeUnit.toMillis(Long.parseLong(trimmed));
  }

  private @Nullable String resolve(final @NotNull String value) {
    if (resolver == null || value.isEmpty()) {
      return value;
    }
    return resolver.resolveStringValue(value);
  }

  @Override
  public void setEmbeddedValueResolver(StringValueResolver resolver) {
    this.resolver = resolver;
  }
}

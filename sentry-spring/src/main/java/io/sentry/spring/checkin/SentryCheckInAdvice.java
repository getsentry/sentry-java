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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
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
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringValueResolver;

/**
 * Reports execution of every bean method annotated with {@link SentryCheckIn} as a monitor
 * check-in.
 */
@ApiStatus.Internal
@Open
public class SentryCheckInAdvice implements MethodInterceptor, EmbeddedValueResolverAware {
  // Spring before 5.3 parses crons with CronSequenceGenerator
  private static final boolean LEGACY_CRON_PARSER =
      !ClassUtils.isPresent(
          "org.springframework.scheduling.support.CronExpression",
          SentryCheckInAdvice.class.getClassLoader());

  private static final @NotNull Pattern WORD_BOUNDARY =
      Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
  private static final int MAX_SLUG_LENGTH = 50;

  private final @NotNull IScopes scopes;

  private final boolean monitorScheduledTasks;

  private @Nullable StringValueResolver resolver;

  private final @NotNull Map<Method, CachedMonitorConfig> monitorConfigs =
      new ConcurrentHashMap<>();

  public SentryCheckInAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public SentryCheckInAdvice(final @NotNull IScopes scopes) {
    this(scopes, false);
  }

  /**
   * @param monitorScheduledTasks whether to also monitor {@code @Scheduled} methods without {@link
   *     SentryCheckIn}, using a slug derived from the class and method name
   */
  public SentryCheckInAdvice(final @NotNull IScopes scopes, final boolean monitorScheduledTasks) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.monitorScheduledTasks = monitorScheduledTasks;
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    @Nullable
    SentryCheckIn checkInAnnotation =
        AnnotationUtils.findAnnotation(mostSpecificMethod, SentryCheckIn.class);
    if (checkInAnnotation == null) {
      if (monitorScheduledTasks && isScheduled(mostSpecificMethod)) {
        final @NotNull String taskName =
            scheduledTaskName(invocation.getThis().getClass(), mostSpecificMethod);
        final @Nullable MonitorConfig monitorConfig = monitorConfig(mostSpecificMethod);
        if (monitorConfig == null) {
          scopes
              .getOptions()
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Not monitoring %s because Sentry Crons can't express its @Scheduled schedule.",
                  taskName);
          return invocation.proceed();
        }
        return captureCheckIns(invocation, scheduledTaskSlug(taskName), false, monitorConfig);
      }
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

    final @Nullable MonitorConfig monitorConfig =
        !isHeartbeatOnly && checkInAnnotation.upsertMonitorConfig()
            ? monitorConfig(mostSpecificMethod)
            : null;
    return captureCheckIns(invocation, monitorSlug, isHeartbeatOnly, monitorConfig);
  }

  private Object captureCheckIns(
      final @NotNull MethodInvocation invocation,
      final @NotNull String monitorSlug,
      final boolean isHeartbeatOnly,
      final @Nullable MonitorConfig monitorConfig)
      throws Throwable {
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
          inProgress.setMonitorConfig(monitorConfig);
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

  static boolean isScheduled(final @NotNull Method method) {
    return !AnnotatedElementUtils.findMergedRepeatableAnnotations(method, Scheduled.class)
        .isEmpty();
  }

  /** {@code ClassName.methodName}, with nested classes as {@code Outer.Inner}. */
  static @NotNull String scheduledTaskName(
      final @NotNull Class<?> targetClass, final @NotNull Method method) {
    return ClassUtils.getShortName(ClassUtils.getUserClass(targetClass)) + "." + method.getName();
  }

  static @NotNull String scheduledTaskSlug(final @NotNull String taskName) {
    final @NotNull String slug =
        WORD_BOUNDARY
            .matcher(taskName)
            .replaceAll("-")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    if (slug.length() <= MAX_SLUG_LENGTH) {
      return slug;
    }
    // keep truncated slugs unique
    final @NotNull CRC32 crc = new CRC32();
    crc.update(slug.getBytes(StandardCharsets.UTF_8));
    final @NotNull String hash = String.format(Locale.ROOT, "%08x", crc.getValue());
    return slug.substring(0, MAX_SLUG_LENGTH - hash.length() - 1).replaceAll("-+$", "")
        + "-"
        + hash;
  }

  private @Nullable MonitorConfig monitorConfig(final @NotNull Method method) {
    return monitorConfigs.computeIfAbsent(
            method, key -> new CachedMonitorConfig(monitorConfigFromScheduled(key)))
        .config;
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
      // Spring only reads the zone for a cron
      final @Nullable String zone =
          cron == null || cron.isEmpty() ? null : resolve(scheduled.zone());
      return MonitorConfigUtils.fromSpringScheduled(
          cron,
          zone,
          periodMillis(scheduled.fixedRate(), scheduled.fixedRateString(), timeUnit),
          periodMillis(scheduled.fixedDelay(), scheduled.fixedDelayString(), timeUnit),
          LEGACY_CRON_PARSER);
    } catch (RuntimeException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Could not derive a monitor config from @Scheduled.", e);
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
    @Nullable Long millis = null;
    if (trimmed.startsWith("P") || trimmed.startsWith("p")) {
      try {
        millis = Duration.parse(trimmed).toMillis();
      } catch (DateTimeParseException | ArithmeticException e) {
        // logged below
      }
    } else {
      millis = MonitorConfigUtils.parsePeriodMillis(trimmed, timeUnit);
    }
    if (millis == null) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not sending a monitor config because the @Scheduled period '%s' could not be parsed.",
              trimmed);
    }
    return millis;
  }

  private @Nullable String resolve(final @NotNull String value) {
    if (resolver == null || value.isEmpty()) {
      return value;
    }
    return resolver.resolveStringValue(value);
  }

  private static final class CachedMonitorConfig {
    private final @Nullable MonitorConfig config;

    private CachedMonitorConfig(final @Nullable MonitorConfig config) {
      this.config = config;
    }
  }

  @Override
  public void setEmbeddedValueResolver(StringValueResolver resolver) {
    this.resolver = resolver;
  }
}

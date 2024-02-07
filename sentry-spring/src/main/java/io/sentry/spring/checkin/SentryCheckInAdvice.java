package io.sentry.spring.checkin;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.DateUtils;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringValueResolver;

/**
 * Reports execution of every bean method annotated with {@link SentryCheckIn} as a monitor
 * check-in.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Open
public class SentryCheckInAdvice implements MethodInterceptor, EmbeddedValueResolverAware {
  private final @NotNull IHub hub;

  private @Nullable StringValueResolver resolver;

  public SentryCheckInAdvice() {
    this(HubAdapter.getInstance());
  }

  public SentryCheckInAdvice(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
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
        hub.getOptions()
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Slug for method annotated with @SentryCheckIn could not be resolved from properties.",
                e);
      }
    }

    if (ObjectUtils.isEmpty(monitorSlug)) {
      hub.getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Not capturing check-in for method annotated with @SentryCheckIn because it does not specify a monitor slug.");
      return invocation.proceed();
    }

    hub.pushScope();
    TracingUtils.startNewTrace(hub);

    @Nullable SentryId checkInId = null;
    final long startTime = System.currentTimeMillis();
    boolean didError = false;

    try {
      if (!isHeartbeatOnly) {
        checkInId = hub.captureCheckIn(new CheckIn(monitorSlug, CheckInStatus.IN_PROGRESS));
      }
      return invocation.proceed();
    } catch (Throwable e) {
      didError = true;
      throw e;
    } finally {
      final @NotNull CheckInStatus status = didError ? CheckInStatus.ERROR : CheckInStatus.OK;
      CheckIn checkIn = new CheckIn(checkInId, monitorSlug, status);
      checkIn.setDuration(DateUtils.millisToSeconds(System.currentTimeMillis() - startTime));
      hub.captureCheckIn(checkIn);
      hub.popScope();
    }
  }

  @Override
  public void setEmbeddedValueResolver(StringValueResolver resolver) {
    this.resolver = resolver;
  }
}

package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

/** Captures an exception passed to a bean method annotated with {@link SentryCaptureException}. */
@ApiStatus.Internal
@Open
public class SentryCaptureExceptionAdvice implements MethodInterceptor {
  private static final String MECHANISM_TYPE = "SentrySpring6CaptureExceptionAdvice";
  private final @NotNull IHub hub;

  public SentryCaptureExceptionAdvice(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
    SentryCaptureException sentryCaptureException =
        AnnotationUtils.findAnnotation(mostSpecificMethod, SentryCaptureException.class);

    if (sentryCaptureException != null) {
      Object[] args = invocation.getArguments();
      for (Object arg : args) {
        if (arg instanceof Exception) {
          captureException((Exception) arg);
          break;
        }
      }
    }

    return invocation.proceed();
  }

  private void captureException(final @NotNull Throwable throwable) {
    final Mechanism mechanism = new Mechanism();
    mechanism.setType(MECHANISM_TYPE);
    mechanism.setHandled(true);
    final Throwable mechanismException =
        new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
    hub.captureException(mechanismException);
  }
}

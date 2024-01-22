package io.sentry.spring.jakarta.exception;

import com.jakewharton.nopen.annotation.Open;

import io.sentry.HubAdapter;
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

/**
 * Captures an exception passed to a bean method annotated with {@link
 * SentryCaptureExceptionParameter}.
 */
@ApiStatus.Internal
@Open
public class SentryCaptureExceptionParameterAdvice implements MethodInterceptor {
  private static final String MECHANISM_TYPE = "SentrySpring6CaptureExceptionParameterAdvice";
  private final @NotNull IHub hub;

  public SentryCaptureExceptionParameterAdvice() {
    this.hub = HubAdapter.getInstance();
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
    SentryCaptureExceptionParameter sentryCaptureExceptionParameter =
        AnnotationUtils.findAnnotation(mostSpecificMethod, SentryCaptureExceptionParameter.class);

    if (sentryCaptureExceptionParameter != null) {
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

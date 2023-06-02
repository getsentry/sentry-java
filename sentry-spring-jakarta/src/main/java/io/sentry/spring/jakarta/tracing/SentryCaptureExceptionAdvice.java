package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.Sentry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

/**
 * CaptureException execution of every bean method annotated with {@link SentryCaptureException}.
 */

@ApiStatus.Internal
@Open
public class SentryCaptureExceptionAdvice implements MethodInterceptor {
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
      AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
    SentryCaptureException sentryCaptureException = AnnotationUtils.findAnnotation(mostSpecificMethod, SentryCaptureException.class);

    if (sentryCaptureException != null) {
      Object[] args = invocation.getArguments();
      for (Object arg : args) {
        if (arg instanceof Exception) {
          Sentry.captureException((Exception) arg);
          break;
        }
      }
    }

    try {
      final Object result = invocation.proceed();
      return result;
    } catch (Throwable e) {
      throw e;
    }
  }
}

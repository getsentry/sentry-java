package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Creates a span from every bean method executed within {@link SentryTransaction}. Depending on the
 * configured pointcut, method either must or can be annotated with {@link SentrySpan}.
 */
@Open
public class SentrySpanAdvice implements MethodInterceptor {
  private final @NotNull IHub hub;

  public SentrySpanAdvice(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final ISpan activeSpan = hub.getSpan();

    if (activeSpan == null || activeSpan.isNoOp()) {
      // there is no active transaction, we do not start new span
      return invocation.proceed();
    } else {
      final Method mostSpecificMethod =
          AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
      final Class<?> targetClass = invocation.getMethod().getDeclaringClass();
      SentrySpan sentrySpan = AnnotationUtils.findAnnotation(mostSpecificMethod, SentrySpan.class);
      if (sentrySpan == null) {
        sentrySpan =
            AnnotationUtils.findAnnotation(
                mostSpecificMethod.getDeclaringClass(), SentrySpan.class);
      }
      final String operation = resolveSpanOperation(targetClass, mostSpecificMethod, sentrySpan);
      final ISpan span = activeSpan.startChild(operation);
      span.getSpanContext().setOrigin("auto.spring");
      if (sentrySpan != null && !StringUtils.isEmpty(sentrySpan.description())) {
        span.setDescription(sentrySpan.description());
      }
      try {
        final Object result = invocation.proceed();
        span.setStatus(SpanStatus.OK);
        return result;
      } catch (Throwable e) {
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        span.setThrowable(e);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private String resolveSpanOperation(
      Class<?> targetClass, Method method, @Nullable SentrySpan sentrySpan) {
    return sentrySpan == null || StringUtils.isEmpty(sentrySpan.value())
        ? targetClass.getSimpleName() + "." + method.getName()
        : sentrySpan.value();
  }
}

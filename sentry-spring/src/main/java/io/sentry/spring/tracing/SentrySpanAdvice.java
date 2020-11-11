package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
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

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    final ISpan activeSpan = resolveActiveSpan();

    if (activeSpan == null) {
      // there is no active transaction, we do not start new span
      return invocation.proceed();
    } else {
      final ISpan span = activeSpan.startChild();
      final SentrySpan sentrySpan =
          AnnotationUtils.findAnnotation(mostSpecificMethod, SentrySpan.class);
      span.setDescription(resolveSpanDescription(mostSpecificMethod, sentrySpan));
      if (sentrySpan != null && !StringUtils.isEmpty(sentrySpan.op())) {
        span.setOp(sentrySpan.op());
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

  private String resolveSpanDescription(Method method, @Nullable SentrySpan sentrySpan) {
    return sentrySpan == null || StringUtils.isEmpty(sentrySpan.value())
        ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
        : sentrySpan.value();
  }

  private @Nullable ISpan resolveActiveSpan() {
    final AtomicReference<ISpan> spanRef = new AtomicReference<>();

    hub.configureScope(
        scope -> {
          final ISpan span = scope.getSpan();

          if (span != null) {
            spanRef.set(span);
          }
        });

    return spanRef.get();
  }
}

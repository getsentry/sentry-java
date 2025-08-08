package io.sentry.spring7.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.ISpan;
import io.sentry.ScopesAdapter;
import io.sentry.SpanOptions;
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
  private static final String TRACE_ORIGIN = "auto.function.spring7.advice";
  private final @NotNull IScopes scopes;

  public SentrySpanAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public SentrySpanAdvice(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final ISpan activeSpan = scopes.getSpan();

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
      SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGIN);
      final ISpan span = activeSpan.startChild(operation, null, spanOptions);
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

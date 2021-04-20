package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Reports execution of every bean method annotated with {@link SentryTransaction} or a execution of
 * a bean method within a class annotated with {@link SentryTransaction}.
 */
@ApiStatus.Internal
@Open
public class SentryTransactionAdvice implements MethodInterceptor {
  private final @NotNull IHub hub;

  public SentryTransactionAdvice(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    @Nullable
    SentryTransaction sentryTransaction =
        AnnotationUtils.findAnnotation(mostSpecificMethod, SentryTransaction.class);
    if (sentryTransaction == null) {
      sentryTransaction =
          AnnotationUtils.findAnnotation(
              mostSpecificMethod.getDeclaringClass(), SentryTransaction.class);
    }

    final String name = resolveTransactionName(invocation, sentryTransaction);

    final boolean isTransactionActive = isTransactionActive();

    if (isTransactionActive) {
      // transaction is already active, we do not start new transaction
      return invocation.proceed();
    } else {
      String operation;
      if (sentryTransaction != null && !StringUtils.isEmpty(sentryTransaction.operation())) {
        operation = sentryTransaction.operation();
      } else {
        operation = "bean";
      }
      hub.pushScope();
      final ITransaction transaction = hub.startTransaction(name, operation, true);
      try {
        final Object result = invocation.proceed();
        transaction.setStatus(SpanStatus.OK);
        return result;
      } catch (Exception e) {
        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
        transaction.finish();
        hub.popScope();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private @NotNull String resolveTransactionName(
      MethodInvocation invocation, @Nullable SentryTransaction sentryTransaction) {
    return sentryTransaction == null || StringUtils.isEmpty(sentryTransaction.value())
        ? invocation.getMethod().getDeclaringClass().getSimpleName()
            + "."
            + invocation.getMethod().getName()
        : sentryTransaction.value();
  }

  private boolean isTransactionActive() {
    return hub.getSpan() != null;
  }
}

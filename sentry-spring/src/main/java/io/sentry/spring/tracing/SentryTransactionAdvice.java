package io.sentry.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.util.Objects;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
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
      final ISpan transaction = hub.startTransaction(name, operation);
      hub.configureScope(scope -> scope.setTransaction(transaction));

      try {
        return invocation.proceed();
      } finally {
        transaction.finish();
      }
    }
  }

  private @NotNull String resolveTransactionName(
      MethodInvocation invocation, @Nullable SentryTransaction sentryTransaction) {
    return sentryTransaction == null || StringUtils.isEmpty(sentryTransaction.value())
        ? invocation.getMethod().getDeclaringClass().getSimpleName()
            + "."
            + invocation.getMethod().getName()
        : sentryTransaction.value();
  }

  private boolean isTransactionActive() {
    AtomicBoolean isTransactionActiveRef = new AtomicBoolean(false);

    hub.configureScope(
        scope -> {
          ISpan span = scope.getSpan();

          if (span != null) {
            isTransactionActiveRef.set(true);
          }
        });
    return isTransactionActiveRef.get();
  }
}

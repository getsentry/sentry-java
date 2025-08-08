package io.sentry.spring7.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ITransaction;
import io.sentry.ScopesAdapter;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.protocol.TransactionNameSource;
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
  private static final String TRACE_ORIGIN = "auto.function.spring_jakarta.advice";

  private final @NotNull IScopes scopesBeforeForking;

  public SentryTransactionAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public SentryTransactionAdvice(final @NotNull IScopes scopes) {
    this.scopesBeforeForking = Objects.requireNonNull(scopes, "scopes are required");
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

    final TransactionNameAndSource nameAndSource =
        resolveTransactionName(invocation, sentryTransaction);

    final boolean isTransactionActive = isTransactionActive(scopesBeforeForking);

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
      final @NotNull IScopes forkedScopes =
          scopesBeforeForking.forkedScopes("SentryTransactionAdvice");
      try (final @NotNull ISentryLifecycleToken ignored = forkedScopes.makeCurrent()) {
        final TransactionOptions transactionOptions = new TransactionOptions();
        transactionOptions.setBindToScope(true);
        transactionOptions.setOrigin(TRACE_ORIGIN);
        final ITransaction transaction =
            forkedScopes.startTransaction(
                new TransactionContext(nameAndSource.name, nameAndSource.source, operation),
                transactionOptions);
        try {
          final Object result = invocation.proceed();
          transaction.setStatus(SpanStatus.OK);
          return result;
        } catch (Throwable e) {
          transaction.setStatus(SpanStatus.INTERNAL_ERROR);
          transaction.setThrowable(e);
          throw e;
        } finally {
          transaction.finish();
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  private @NotNull TransactionNameAndSource resolveTransactionName(
      MethodInvocation invocation, @Nullable SentryTransaction sentryTransaction) {
    if (sentryTransaction == null || StringUtils.isEmpty(sentryTransaction.value())) {
      final String name =
          invocation.getMethod().getDeclaringClass().getSimpleName()
              + "."
              + invocation.getMethod().getName();
      return new TransactionNameAndSource(name, TransactionNameSource.COMPONENT);
    } else {
      return new TransactionNameAndSource(sentryTransaction.value(), TransactionNameSource.CUSTOM);
    }
  }

  private boolean isTransactionActive(final @NotNull IScopes scopes) {
    return scopes.getSpan() != null;
  }

  private static class TransactionNameAndSource {
    private final @NotNull String name;
    private final @NotNull TransactionNameSource source;

    public TransactionNameAndSource(
        final @NotNull String name, final @NotNull TransactionNameSource source) {
      this.name = name;
      this.source = source;
    }
  }
}

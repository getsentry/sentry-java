package io.sentry.spring.jakarta.tracing;

import io.sentry.protocol.SentryTransaction;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Makes annotated method execution or a method execution within a class annotated with {@link
 * SentrySpan} executed within running {@link SentryTransaction} to get wrapped into {@link
 * io.sentry.Span}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SentrySpan {

  /**
   * Span description.
   *
   * @return description
   */
  String description() default "";

  /**
   * Span operation. If not set, operation is resolved as a class name and a method name.
   *
   * @return operation.
   */
  @AliasFor("value")
  String operation() default "";

  /**
   * Span operation. If not set, transaction name is resolved as a class name and a method name.
   *
   * @return operation.
   */
  @AliasFor("operation")
  String value() default "";
}

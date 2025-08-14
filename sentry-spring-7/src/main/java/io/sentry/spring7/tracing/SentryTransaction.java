package io.sentry.spring7.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Makes annotated method execution or a method execution within an annotated class to get wrapped
 * into {@link io.sentry.protocol.SentryTransaction}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SentryTransaction {

  /**
   * Transaction name. If not set, transaction name is resolved as a class name and a method name.
   *
   * @return transaction name
   */
  @AliasFor("value")
  String name() default "";

  /**
   * A transaction operation, for example "http".
   *
   * @return transaction operation
   */
  String operation();

  /**
   * Transaction name. If not set, transaction name is resolved as a class name and a method name.
   *
   * @return transaction name
   */
  @AliasFor("name")
  String value() default "";
}

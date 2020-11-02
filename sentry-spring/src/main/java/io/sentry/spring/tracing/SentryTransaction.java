package io.sentry.spring.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Bean method annotated with {@link SentryTransaction} gets wrapped into {@link
 * io.sentry.SentryTransaction}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SentryTransaction {

  /**
   * Transaction name.
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
  String op() default "";

  /**
   * Transaction name.
   *
   * @return transaction name
   */
  @AliasFor("name")
  String value() default "";
}

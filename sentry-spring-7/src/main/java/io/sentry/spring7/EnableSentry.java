package io.sentry.spring7;

import io.sentry.SentryOptions;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables Sentry error handling capabilities.
 *
 * <ul>
 *   <li>creates bean of type {@link io.sentry.SentryOptions}
 *   <li>registers {@link io.sentry.IScopes} for sending Sentry events
 *   <li>registers {@link SentryExceptionResolver} to send Sentry event for any uncaught exception
 *       in Spring MVC flow.
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Import({SentryHubRegistrar.class, SentryInitBeanPostProcessor.class, SentryWebConfiguration.class})
@Target(ElementType.TYPE)
public @interface EnableSentry {

  /**
   * The DSN tells the SDK where to send the events to. If this value is not provided, the SDK will
   * just not send any events.
   *
   * @return the Sentry DSN
   */
  String dsn() default "";

  /**
   * Whether to send personal identifiable information along with events.
   *
   * @return true if send default PII or false otherwise.
   */
  boolean sendDefaultPii() default false;

  /**
   * Determines whether all web exceptions are reported or only uncaught exceptions.
   *
   * @return the order to use for {@link SentryExceptionResolver}
   */
  int exceptionResolverOrder() default 1;

  /**
   * Controls the size of the request body to extract if any. No truncation is done by the SDK. If
   * the request body is larger than the accepted size, nothing is sent.
   */
  SentryOptions.RequestSize maxRequestBodySize() default SentryOptions.RequestSize.NONE;
}

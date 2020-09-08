package io.sentry.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables Sentry error handling capabilities.
 *
 * <ul>
 *   <li>creates bean of type {@link io.sentry.core.SentryOptions}
 *   <li>registers {@link io.sentry.core.IHub} for sending Sentry events
 *   <li>registers {@link SentryRequestFilter} for attaching request information to Sentry events
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
   */
  String dsn() default "";

  /** Whether to send personal identifiable information along with events. */
  boolean sendDefaultPii() default false;
}

package io.sentry.spring7.checkin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/** Sends a {@link io.sentry.CheckIn} for the annotated method. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SentryCheckIn {

  /**
   * Monitor slug. If not set, no check-in will be sent.
   *
   * @return monitor slug
   */
  @AliasFor("value")
  String monitorSlug() default "";

  /**
   * Whether to send only send heartbeat events.
   *
   * <p>A hearbeat check-in means there's no separate IN_PROGRESS check-in at the start of the jobs
   * execution. Only the check-in after finishing the job will be sent.
   *
   * @return true if only heartbeat check-ins should be sent.
   */
  boolean heartbeat() default false;

  /**
   * Whether to send a monitor config from the method's {@code @Scheduled}, so Sentry creates or
   * updates the monitor. Schedules that can't be converted send none. Not used for heartbeats.
   *
   * @return true to send a monitor config (default)
   */
  boolean upsertMonitorConfig() default true;

  /**
   * Monitor slug. If not set, no check-in will be sent.
   *
   * @return monitor slug
   */
  @AliasFor("monitorSlug")
  String value() default "";
}

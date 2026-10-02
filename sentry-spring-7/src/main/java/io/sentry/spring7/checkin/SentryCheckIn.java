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
   * Whether to send a monitor config derived from the method's {@link
   * org.springframework.scheduling.annotation.Scheduled} annotation, so Sentry creates or updates
   * the monitor's schedule (and timezone) from code.
   *
   * <p>Only cron expressions with a fixed seconds field, and fixed rates or delays that are a whole
   * number of minutes, can be converted. Other schedules send no monitor config. Not used for
   * heartbeat check-ins.
   *
   * @return true if a monitor config should be sent with the check-in.
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

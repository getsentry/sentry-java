package io.sentry.spring.jakarta.exception;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Captures an exception passed to an annotated method. Can be used to capture exceptions from your
 * {@link org.springframework.web.bind.annotation.ExceptionHandler} but can also be used on other
 * methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SentryCaptureExceptionParameter {}

package io.sentry.spring.jakarta.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If there is an Exception in the method argument,
 * running captureException(final @NotNull Throwable throwable) in {@link io.sentry.Sentry}.
 * for {@link org.springframework.web.bind.annotation.ExceptionHandler}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SentryCaptureException {

}

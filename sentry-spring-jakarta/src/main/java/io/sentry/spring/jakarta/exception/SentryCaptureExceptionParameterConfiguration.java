package io.sentry.spring.jakarta.exception;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides infrastructure beans for capturing exceptions passed to bean methods annotated with
 * {@link SentryCaptureExceptionParameter}.
 */
@Configuration
@Import({
  SentryExceptionParameterAdviceConfiguration.class,
  SentryCaptureExceptionParameterPointcutConfiguration.class
})
@Open
public class SentryCaptureExceptionParameterConfiguration {}

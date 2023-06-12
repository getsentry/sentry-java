package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides infrastructure beans for capturing exceptions passed to bean methods annotated with
 * {@link SentryCaptureException}.
 */
@Configuration
@Import({
  SentryExceptionAdviceConfiguration.class,
  SentryCaptureExceptionPointcutConfiguration.class
})
@Open
public class SentryCaptureExceptionConfiguration {}

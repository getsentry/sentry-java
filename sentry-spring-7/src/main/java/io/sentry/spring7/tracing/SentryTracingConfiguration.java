package io.sentry.spring7.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides infrastructure beans for creating transactions and spans around bean methods annotated
 * with {@link SentryTransaction} and {@link SentrySpan}.
 */
@Configuration
@Import({
  SentryAdviceConfiguration.class,
  SentrySpanPointcutConfiguration.class,
  SentryTransactionPointcutConfiguration.class
})
@Open
public class SentryTracingConfiguration {}

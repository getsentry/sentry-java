package io.sentry.samples.spring;

import io.sentry.spring.EnableSentry;
import io.sentry.spring.tracing.SentryTracingConfiguration;
import org.springframework.context.annotation.Import;

// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry
// project/dashboard
@EnableSentry(
  dsn = "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563",
  sendDefaultPii = true)
@Import(SentryTracingConfiguration.class)
public class SentryConfig {
}

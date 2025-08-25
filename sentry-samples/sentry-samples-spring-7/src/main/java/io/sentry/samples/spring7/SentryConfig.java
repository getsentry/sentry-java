package io.sentry.samples.spring7;

import io.sentry.SentryOptions;
import io.sentry.SentryOptions.TracesSamplerCallback;
import io.sentry.spring7.EnableSentry;
import io.sentry.spring7.tracing.SentryTracingConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry
// project/dashboard
@EnableSentry(
    dsn = "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563",
    sendDefaultPii = true,
    maxRequestBodySize = SentryOptions.RequestSize.MEDIUM)
@Import(SentryTracingConfiguration.class)
public class SentryConfig {

  /**
   * Configures callback used to determine if transaction should be sampled.
   *
   * @return traces sampler callback
   */
  @Bean
  TracesSamplerCallback tracesSamplerCallback() {
    return samplingContext -> {
      HttpServletRequest request =
          (HttpServletRequest) samplingContext.getCustomSamplingContext().get("request");
      if ("/error".equals(request.getRequestURI())) {
        return 0.5d;
      } else {
        return 1.0d;
      }
    };
  }
}

package io.sentry.samples.spring;

import io.sentry.IHub;
import io.sentry.SentryOptions.TracesSamplerCallback;
import io.sentry.spring.EnableSentry;
import io.sentry.spring.SentryUserFilter;
import io.sentry.spring.SentryUserProvider;
import io.sentry.spring.SpringSecuritySentryUserProvider;
import io.sentry.spring.tracing.SentryTracingConfiguration;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry
// project/dashboard
@EnableSentry(
    dsn = "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563",
    sendDefaultPii = true)
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

  @Bean
  SentryUserFilter sentryUserFilter(
      final IHub hub, final List<SentryUserProvider> sentryUserProviders) {
    return new SentryUserFilter(hub, sentryUserProviders);
  }

  @Bean
  SpringSecuritySentryUserProvider springSecuritySentryUserProvider() {
    return new SpringSecuritySentryUserProvider();
  }
}

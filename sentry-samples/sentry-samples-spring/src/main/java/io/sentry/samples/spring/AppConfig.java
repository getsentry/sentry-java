package io.sentry.samples.spring;

import io.sentry.SentryOptions.TracesSamplerCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.servlet.http.HttpServletRequest;

@Configuration
@Import(SentryConfig.class)
public class AppConfig {

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

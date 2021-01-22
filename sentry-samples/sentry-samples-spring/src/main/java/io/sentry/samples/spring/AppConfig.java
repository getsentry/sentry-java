package io.sentry.samples.spring;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SentryConfig.class)
public class AppConfig {}

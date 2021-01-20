package io.sentry.samples.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@ComponentScan("io.sentry.samples.spring.web")
@EnableWebMvc
public class WebConfig {}

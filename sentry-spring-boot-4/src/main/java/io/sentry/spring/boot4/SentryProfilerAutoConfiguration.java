package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.spring7.SentryProfilerConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {"io.sentry.opentelemetry.agent.AgentMarker"})
@Open
@Import(SentryProfilerConfiguration.class)
public class SentryProfilerAutoConfiguration {}

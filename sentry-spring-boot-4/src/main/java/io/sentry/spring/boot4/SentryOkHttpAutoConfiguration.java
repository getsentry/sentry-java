package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.okhttp.SentryOkHttpEventListener;
import io.sentry.okhttp.SentryOkHttpInterceptor;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configures Sentry instrumentation for Spring-managed {@link OkHttpClient} beans. */
@Configuration(proxyBeanMethods = false)
@Open
@ConditionalOnClass({
  OkHttpClient.class,
  SentryOkHttpInterceptor.class,
  SentryOkHttpEventListener.class
})
@ConditionalOnProperty(name = "sentry.dsn")
public class SentryOkHttpAutoConfiguration {

  @Bean
  static @NotNull SentryOkHttpClientBeanPostProcessor sentryOkHttpClientBeanPostProcessor() {
    return new SentryOkHttpClientBeanPostProcessor();
  }
}

package io.sentry.spring.boot.datasource.dsproxy;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.dsproxy.SentryQueryExecutionListener;
import io.sentry.spring.boot.SentryTracingCondition;
import javax.sql.DataSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** Auto-configures datasource-proxy related beans. */
@Open
@Configuration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, SentryQueryExecutionListener.class})
@Conditional(SentryTracingCondition.class)
@ConditionalOnBean(IHub.class)
public class SentryDsProxyAutoConfiguration {

  @Bean
  public @NotNull SentryQueryExecutionListener sentryQueryExecutionListener(
      final @NotNull IHub hub) {
    return new SentryQueryExecutionListener(hub);
  }
}

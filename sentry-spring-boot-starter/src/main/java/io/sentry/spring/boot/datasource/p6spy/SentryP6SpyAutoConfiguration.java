package io.sentry.spring.boot.datasource.p6spy;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.p6spy.SentryJdbcEventListener;
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

/** Auto-configures P6Spy related beans. */
@Open
@Configuration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, SentryJdbcEventListener.class})
@Conditional(SentryTracingCondition.class)
@ConditionalOnBean(IHub.class)
public class SentryP6SpyAutoConfiguration {

  @Bean
  public @NotNull SentryJdbcEventListener sentryJdbcEventListener(final @NotNull IHub hub) {
    return new SentryJdbcEventListener(hub);
  }
}

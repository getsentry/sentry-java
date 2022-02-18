package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.jdbc.NoOpLogger;
import io.sentry.jdbc.SentryJdbcEventListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-Configurations for P6Spy and sentry-jdbc module. */
@Configuration
@ConditionalOnClass(SentryJdbcEventListener.class)
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@Open
public class SentryP6SpyAutoConfiguration {

  @Bean
  public @NotNull P6SpyLogsDisabler p6SpyLogsDisabler(final @NotNull SentryProperties properties) {
    return new P6SpyLogsDisabler(properties);
  }

  /** Disables creating spy.log file by P6Spy. */
  @Open
  static class P6SpyLogsDisabler implements InitializingBean {

    private final @NotNull SentryProperties properties;

    P6SpyLogsDisabler(final @NotNull SentryProperties properties) {
      this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
      if (properties.getJdbc().isDisableLogFile()) {
        System.setProperty("p6spy.config.appender", NoOpLogger.class.getCanonicalName());
      }
    }
  }
}

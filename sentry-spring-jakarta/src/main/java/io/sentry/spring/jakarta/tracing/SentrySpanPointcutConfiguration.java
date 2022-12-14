package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AOP pointcut configuration for {@link SentrySpan}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentrySpanPointcutConfiguration {

  /**
   * Pointcut around which spans are created.
   *
   * @return pointcut used by {@link SentrySpanAdvice}.
   */
  @Bean
  public @NotNull Pointcut sentrySpanPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentrySpan.class, true))
        .union(new AnnotationMatchingPointcut(null, SentrySpan.class));
  }
}

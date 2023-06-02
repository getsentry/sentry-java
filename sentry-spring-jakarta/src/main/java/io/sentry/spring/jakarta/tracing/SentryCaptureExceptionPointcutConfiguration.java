package io.sentry.spring.jakarta.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AOP pointcut configuration for {@link SentryCaptureException}. */
@Configuration(proxyBeanMethods = false)
@Open
public class SentryCaptureExceptionPointcutConfiguration {

  /**
   * Pointcut around which spans are created.
   *
   * @return pointcut used by {@link SentryCaptureExceptionAdvice}.
   */
  @Bean
  public @NotNull Pointcut sentryCaptureExceptionPointcut() {
    return new ComposablePointcut(new AnnotationClassFilter(SentryCaptureException.class, true))
        .union(new AnnotationMatchingPointcut(null, SentryCaptureException.class));
  }
}

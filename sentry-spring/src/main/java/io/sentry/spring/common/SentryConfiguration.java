package io.sentry.spring.common;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.spring.SentryWebConfiguration;
import io.sentry.spring.reactive.SentryWebFluxConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

@Configuration
@Open
public class SentryConfiguration implements ImportBeanDefinitionRegistrar {

  private enum ApplicationType {
    SERVLET,
    REACTIVE,
    NONE
  }

  private static final String SERVLET_APPLICATION_ENVIRONMENT_CLASS =
      "org.springframework.web.context.support.StandardServletEnvironment";
  private static final String REACTIVE_APPLICATION_ENVIRONMENT_CLASS =
      "org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment";

  private static final String SERVLET_WEB_APPLICATION_CLASS =
      "org.springframework.web.servlet.HandlerExceptionResolver";
  private static final String REACTIVE_WEB_APPLICATION_CLASS =
      "org.springframework.web.reactive.DispatcherHandler";

  private @NotNull final Environment environment;

  public SentryConfiguration(final @NotNull Environment environment) {
    this.environment = environment;
  }

  @Override
  public void registerBeanDefinitions(
      final @NotNull AnnotationMetadata importingClassMetadata,
      final @NotNull BeanDefinitionRegistry registry) {
    switch (deduceApplicationType()) {
      case REACTIVE:
        configureReactiveApplication(registry);
        break;
      case SERVLET:
        configureServletApplication(registry);
        break;
      default:
    }
  }

  private void configureReactiveApplication(final @NotNull BeanDefinitionRegistry registry) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryWebFluxConfiguration.class);
    registry.registerBeanDefinition("sentryWebFluxConfiguration", builder.getBeanDefinition());
  }

  private void configureServletApplication(final @NotNull BeanDefinitionRegistry registry) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryWebConfiguration.class);
    registry.registerBeanDefinition("sentryWebConfiguration", builder.getBeanDefinition());
  }

  private ApplicationType deduceApplicationType() {
    final ApplicationType type = deduceFromEnvironment(environment.getClass());
    if (type != ApplicationType.NONE) {
      return type;
    } else {
      return deduceFromClasspath();
    }
  }

  private static ApplicationType deduceFromEnvironment(final @NotNull Class<?> envClass) {
    if (isAssignable(SERVLET_APPLICATION_ENVIRONMENT_CLASS, envClass)) {
      return ApplicationType.SERVLET;
    } else if (isAssignable(REACTIVE_APPLICATION_ENVIRONMENT_CLASS, envClass)) {
      return ApplicationType.REACTIVE;
    } else {
      return ApplicationType.NONE;
    }
  }

  private static ApplicationType deduceFromClasspath() {
    if (isPresent(SERVLET_WEB_APPLICATION_CLASS)) {
      return ApplicationType.SERVLET;
    } else if (isPresent(REACTIVE_WEB_APPLICATION_CLASS)) {
      return ApplicationType.REACTIVE;
    } else {
      return ApplicationType.NONE;
    }
  }

  private static boolean isAssignable(String target, Class<?> type) {
    try {
      return ClassUtils.resolveClassName(target, null).isAssignableFrom(type);
    } catch (Throwable ex) {
      return false;
    }
  }

  private static boolean isPresent(String className) {
    try {
      ClassUtils.resolveClassName(className, null);
      return true;
    } catch (Throwable e) {
      return false;
    }
  }
}

package io.sentry.spring.common;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.spring.SentryWebConfiguration;
import io.sentry.spring.reactive.SentryWebFluxConfiguration;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

@Configuration
@Open
public class SentryConfiguration implements BeanClassLoaderAware, ImportBeanDefinitionRegistrar {

  private static final String SERVLET_WEB_APPLICATION_CLASS =
      "org.springframework.web.servlet.HandlerExceptionResolver";
  private static final String REACTIVE_WEB_APPLICATION_CLASS =
      "org.springframework.web.reactive.HandlerResult";

  private ClassLoader beanClassLoader;

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    if (isPresent(SERVLET_WEB_APPLICATION_CLASS)) {
      final BeanDefinitionBuilder builder =
          BeanDefinitionBuilder.genericBeanDefinition(SentryWebConfiguration.class);
      registry.registerBeanDefinition("sentryWebConfiguration", builder.getBeanDefinition());
    } else if (isPresent(REACTIVE_WEB_APPLICATION_CLASS)) {
      final BeanDefinitionBuilder builder =
          BeanDefinitionBuilder.genericBeanDefinition(SentryWebFluxConfiguration.class);
      registry.registerBeanDefinition("sentryWebFluxConfiguration", builder.getBeanDefinition());
    }
  }

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    this.beanClassLoader = classLoader;
  }

  private boolean isPresent(String className) {
    try {
      Class.forName(className, false, beanClassLoader);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}

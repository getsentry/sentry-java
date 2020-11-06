package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.HubAdapter;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/** Registers beans required to use Sentry core features. */
@Configuration
@Open
public class SentryHubRegistrar implements ImportBeanDefinitionRegistrar {

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    final AnnotationAttributes annotationAttributes =
        AnnotationAttributes.fromMap(
            importingClassMetadata.getAnnotationAttributes(EnableSentry.class.getName()));
    if (annotationAttributes != null && annotationAttributes.containsKey("dsn")) {
      registerSentryOptions(registry, annotationAttributes);
      registerSentryHubBean(registry);
      registerSentryExceptionResolver(registry, annotationAttributes);
    }
  }

  private void registerSentryOptions(
      BeanDefinitionRegistry registry, AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryOptions.class);

    if (registry.containsBeanDefinition("mockTransport")) {
      builder.addPropertyReference("transport", "mockTransport");
    }
    builder.addPropertyValue("dsn", annotationAttributes.getString("dsn"));
    builder.addPropertyValue("enableExternalConfiguration", true);
    builder.addPropertyValue("exceptionResolverOrder", annotationAttributes.getNumber("exceptionResolverOrder"));
    builder.addPropertyValue("sentryClientName", BuildConfig.SENTRY_SPRING_SDK_NAME);
    builder.addPropertyValue("sdkVersion", createSdkVersion());
    if (annotationAttributes.containsKey("sendDefaultPii")) {
      builder.addPropertyValue("sendDefaultPii", annotationAttributes.getBoolean("sendDefaultPii"));
    }

    registry.registerBeanDefinition("sentryOptions", builder.getBeanDefinition());
  }

  private void registerSentryHubBean(BeanDefinitionRegistry registry) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(HubAdapter.class);
    builder.setInitMethodName("getInstance");

    registry.registerBeanDefinition("sentryHub", builder.getBeanDefinition());
  }

  private void registerSentryExceptionResolver(
          BeanDefinitionRegistry registry, AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryExceptionResolver.class);
    builder.addConstructorArgReference("sentryHub");
    Integer order = annotationAttributes.getNumber("exceptionResolverOrder");
    builder.addConstructorArgValue(order);

    registry.registerBeanDefinition("sentryExceptionResolver", builder.getBeanDefinition());
  }

  private static @NotNull SdkVersion createSdkVersion() {
    final SentryOptions defaultOptions = new SentryOptions();
    SdkVersion sdkVersion = defaultOptions.getSdkVersion();

    if (sdkVersion == null) {
      sdkVersion = new SdkVersion();
    }

    sdkVersion.setName(BuildConfig.SENTRY_SPRING_SDK_NAME);
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion.setVersion(version);
    sdkVersion.addPackage("maven:sentry-spring", version);

    return sdkVersion;
  }
}

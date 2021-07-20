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
      final @NotNull AnnotationMetadata importingClassMetadata,
      final @NotNull BeanDefinitionRegistry registry) {
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
      final @NotNull BeanDefinitionRegistry registry,
      final @NotNull AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryOptions.class);

    if (registry.containsBeanDefinition("mockTransportFactory")) {
      builder.addPropertyReference("transportFactory", "mockTransportFactory");
    }
    builder.addPropertyValue("dsn", annotationAttributes.getString("dsn"));
    builder.addPropertyValue("enableExternalConfiguration", true);
    builder.addPropertyValue("sentryClientName", BuildConfig.SENTRY_SPRING_SDK_NAME);
    builder.addPropertyValue("sdkVersion", createSdkVersion());
    if (annotationAttributes.containsKey("sendDefaultPii")) {
      builder.addPropertyValue("sendDefaultPii", annotationAttributes.getBoolean("sendDefaultPii"));
    }
    if (annotationAttributes.containsKey("maxRequestBodySize")) {
      builder.addPropertyValue(
          "maxRequestBodySize", annotationAttributes.get("maxRequestBodySize"));
    }

    registry.registerBeanDefinition("sentryOptions", builder.getBeanDefinition());
  }

  private void registerSentryHubBean(final @NotNull BeanDefinitionRegistry registry) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(HubAdapter.class);
    builder.setInitMethodName("getInstance");

    registry.registerBeanDefinition("sentryHub", builder.getBeanDefinition());
  }

  private void registerSentryExceptionResolver(
      final @NotNull BeanDefinitionRegistry registry,
      final @NotNull AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(SentryExceptionResolver.class);
    builder.addConstructorArgReference("sentryHub");
    int order = annotationAttributes.getNumber("exceptionResolverOrder");
    builder.addConstructorArgValue(order);

    registry.registerBeanDefinition("sentryExceptionResolver", builder.getBeanDefinition());
  }

  private static @NotNull SdkVersion createSdkVersion() {
    final SentryOptions defaultOptions = new SentryOptions();
    SdkVersion sdkVersion = defaultOptions.getSdkVersion();

    final String name = BuildConfig.SENTRY_SPRING_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    sdkVersion.addPackage("maven:io.sentry:sentry-spring", version);

    return sdkVersion;
  }
}

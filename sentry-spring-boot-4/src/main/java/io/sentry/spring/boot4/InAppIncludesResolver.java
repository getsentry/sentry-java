package io.sentry.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Resolves {@link SentryProperties} inAppIncludes by getting a package name from a class annotated
 * with {@link SpringBootConfiguration} or another annotation meta-annotated with {@link
 * SpringBootConfiguration} like {@link SpringBootApplication}.
 */
@Open
public class InAppIncludesResolver implements ApplicationContextAware {
  private @Nullable ApplicationContext applicationContext;

  @NotNull
  public List<String> resolveInAppIncludes() {
    if (applicationContext != null) {
      Map<String, Object> beansWithAnnotation =
          applicationContext.getBeansWithAnnotation(SpringBootConfiguration.class);
      return beansWithAnnotation.values().stream()
          .map(bean -> bean.getClass().getPackage().getName())
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public void setApplicationContext(@NotNull ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }
}

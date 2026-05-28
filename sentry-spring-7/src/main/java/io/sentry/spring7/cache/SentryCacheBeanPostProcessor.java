package io.sentry.spring7.cache;

import io.sentry.ScopesAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/** Wraps {@link CacheManager} beans in {@link SentryCacheManagerWrapper} for instrumentation. */
@ApiStatus.Internal
public final class SentryCacheBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

  @Override
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof CacheManager && !(bean instanceof SentryCacheManagerWrapper)) {
      return new SentryCacheManagerWrapper((CacheManager) bean, ScopesAdapter.getInstance());
    }
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}

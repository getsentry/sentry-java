package io.sentry.spring.boot4;

import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import io.sentry.okhttp.SentryOkHttpEventListener;
import io.sentry.okhttp.SentryOkHttpInterceptor;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

final class SentryOkHttpClientBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

  @Override
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (!(bean instanceof OkHttpClient)) {
      return bean;
    }

    final @NotNull OkHttpClient client = (OkHttpClient) bean;
    if (client.getClass() != OkHttpClient.class) {
      ScopesAdapter.getInstance()
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Sentry OkHttp auto-instrumentation skipped for bean '%s' (%s) because replacing "
                  + "an OkHttpClient subclass would not preserve its type. Configure Sentry "
                  + "instrumentation manually for this client.",
              beanName,
              client.getClass().getName());
      return client;
    }

    final boolean addInterceptor = !hasSentryInterceptor(client);
    final boolean wrapEventListener =
        !(client.eventListenerFactory() instanceof SentryEventListenerFactory);
    if (!addInterceptor && !wrapEventListener) {
      return client;
    }

    final @NotNull OkHttpClient.Builder builder = client.newBuilder();
    if (addInterceptor) {
      builder.addInterceptor(new SentryOkHttpInterceptor());
    }
    if (wrapEventListener) {
      builder.eventListenerFactory(new SentryEventListenerFactory(client.eventListenerFactory()));
    }
    return builder.build();
  }

  private static boolean hasSentryInterceptor(final @NotNull OkHttpClient client) {
    for (final @NotNull Interceptor interceptor : client.interceptors()) {
      if (interceptor instanceof SentryOkHttpInterceptor) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private static final class SentryEventListenerFactory implements EventListener.Factory {
    private final @NotNull EventListener.Factory delegate;

    private SentryEventListenerFactory(final @NotNull EventListener.Factory delegate) {
      this.delegate = delegate;
    }

    @Override
    public @NotNull EventListener create(final @NotNull Call call) {
      return new SentryOkHttpEventListener(ScopesAdapter.getInstance(), delegate);
    }
  }
}

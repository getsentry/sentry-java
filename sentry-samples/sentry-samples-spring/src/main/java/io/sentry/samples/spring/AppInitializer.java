package io.sentry.samples.spring;

import io.sentry.spring.tracing.SentryTracingFilter;
import javax.servlet.Filter;

import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class AppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

  @Override
  protected String[] getServletMappings() {
    return new String[] {"/*"};
  }

  @Override
  protected Class<?>[] getRootConfigClasses() {
    return new Class<?>[] {AppConfig.class, SecurityConfiguration.class};
  }

  @Override
  protected Class<?>[] getServletConfigClasses() {
    return new Class<?>[] {WebConfig.class};
  }

  @Override
  protected Filter[] getServletFilters() {
    DelegatingFilterProxy springSecurityFilterChain = new DelegatingFilterProxy();
    springSecurityFilterChain.setTargetBeanName("springSecurityFilterChain");
    return new Filter[] {new SentryTracingFilter(), springSecurityFilterChain};
  }
}

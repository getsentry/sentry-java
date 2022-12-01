package io.sentry.opentelemetry;

import io.opentelemetry.javaagent.tooling.bootstrap.BootstrapPackagesBuilder;
import io.opentelemetry.javaagent.tooling.bootstrap.BootstrapPackagesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.sentry.Sentry;

/**
 * To ensure that the classes we add to bootstrap class loader are available in class loaders that
 * don't delegate all class loading requests to bootstrap class loader e.g. OSGi we need to tell the
 * agent which packages we have added.
 *
 * @see BootstrapPackagesConfigurer
 */
public final class SentryBootstrapPackagesProvider implements BootstrapPackagesConfigurer {

  @Override
  public void configure(BootstrapPackagesBuilder builder, ConfigProperties config) {
    builder.add(Sentry.class.getPackage().getName());
  }
}

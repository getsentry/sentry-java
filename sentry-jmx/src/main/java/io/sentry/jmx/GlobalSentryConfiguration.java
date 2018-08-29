package io.sentry.jmx;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

public class GlobalSentryConfiguration extends AbstractSentryConfiguration implements GlobalSentryConfigurationMBean {
  @Override
  protected SentryClient getClient() {
    return Sentry.getStoredClient();
  }

  @Override
  public void reinitialize(String dsn) {
    Sentry.init(dsn);
  }

  public static void register(MBeanServer server) throws MBeanRegistrationException, InstanceAlreadyExistsException {
    try {
      server.registerMBean(new GlobalSentryConfiguration(), makeName("global"));
    } catch (MalformedObjectNameException | NotCompliantMBeanException e) {
      throw new IllegalStateException(e);
    }
  }
}

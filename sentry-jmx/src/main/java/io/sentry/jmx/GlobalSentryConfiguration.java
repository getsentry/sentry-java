package io.sentry.jmx;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public class GlobalSentryConfiguration extends AbstractSentryConfiguration {
  @Override
  protected SentryClient getClient() {
    return Sentry.getStoredClient();
  }

  @Override
  public void reinitialize(String dsn) {
    Sentry.init(dsn);
  }

  public static ObjectName register(MBeanServer server) throws MBeanRegistrationException, InstanceAlreadyExistsException {
    try {
      return new GlobalSentryConfiguration().register(server, "global");
    } catch (MalformedObjectNameException | NotCompliantMBeanException e) {
      throw new IllegalStateException(e);
    }
  }
}

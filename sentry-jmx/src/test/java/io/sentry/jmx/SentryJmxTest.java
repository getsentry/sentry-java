package io.sentry.jmx;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SentryJmxTest extends BaseTest {
  private MBeanServer beanServer;
  private ObjectName globalBeanName;

  @BeforeMethod
  public void setUp() throws Exception {
    beanServer = ManagementFactory.getPlatformMBeanServer();
    globalBeanName = new ObjectName("io.sentry:type=SentryConfiguration,name=global");

    try {
      beanServer.unregisterMBean(globalBeanName);
    } catch (InstanceNotFoundException e) {
      /* ignore */
    }

    assertThat(beanServer.isRegistered(globalBeanName), is(false));
    GlobalSentryConfiguration.register(beanServer);
    assertThat(beanServer.isRegistered(globalBeanName), is(true));
  }

  @Test
  public void testSetGlobalClientAttributeViaJmx() throws Exception {
    assertNoGlobalSentry();

    SentryConfigurationMBean jmxConfiguration = JMX.newMBeanProxy(beanServer, globalBeanName, SentryConfigurationMBean.class);

    jmxConfiguration.setDist("foo");
    assertThat(Sentry.getStoredClient().getDist(), equalTo("foo"));
  }

  @Test
  public void testSetGlobalDSN() throws Exception {
    assertNoGlobalSentry();
    SentryConfigurationMBean jmxConfiguration = JMX.newMBeanProxy(beanServer, globalBeanName, SentryConfigurationMBean.class);
    jmxConfiguration.reinitialize("http://foo@example.org/3");
    assertThat(Sentry.getStoredClient().toString(), containsString("connection=io.sentry.connection.AsyncConnection"));
  }

  private static void assertNoGlobalSentry() {
    SentryClient c = Sentry.getStoredClient();
    if (c != null) {
      assertThat(Sentry.getStoredClient().toString(), containsString("connection=io.sentry.connection.NoopConnection"));
    }

  }
}

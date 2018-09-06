package io.sentry.jmx;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import io.sentry.BaseTest;
import io.sentry.SentryClient;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.testng.annotations.Test;

public class CustomSentryRegistryJmxTest extends BaseTest {
  private MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
  private SentryRegistry registry = new SentryRegistry();

  @Test
  public void testRegistrationOfRegistryBeans() throws Exception {
    assertThat(getRegisteredInstances(), hasSize(0));
    registry.getClient("one");
    assertThat(getRegisteredInstances(), hasSize(1));
    registry.getClient("two");
    assertThat(getRegisteredInstances(), hasSize(2));
    registry.remove("two");
    assertThat(getRegisteredInstances(), hasSize(1));
    registry.remove("one");
    assertThat(getRegisteredInstances(), hasSize(0));
  }

  @Test
  public void testRegistryClientReinitialization() throws Exception {
    SentryClient client = registry.getClient("one");
    Set<ObjectInstance> instances = getRegisteredInstances();
    assertThat(instances, hasSize(1));
    ObjectName name = instances.iterator().next().getObjectName();
    assertThat(name, equalTo(new ObjectName("io.sentry:type=SentryConfiguration,name=one")));
    SentryConfigurationMXBean proxiedConfiguration = JMX.newMXBeanProxy(beanServer, name, SentryConfigurationMXBean.class);
    assertThat(client.getRelease(), equalTo(proxiedConfiguration.getRelease()));
    client.setRelease("someRelease");
    assertThat(client.getRelease(), equalTo(proxiedConfiguration.getRelease()));
    proxiedConfiguration.reinitialize(null);
    assertThat(client.getRelease(), not(equalTo(proxiedConfiguration.getRelease())));
  }

  private Set<ObjectInstance> getRegisteredInstances() throws Exception {
    return beanServer.queryMBeans(new ObjectName("io.sentry:type=SentryConfiguration,name=*"), null);
  }

  private static class SentryRegistry {
    private Map<String, SentryClient> clients = new HashMap<>();
    private Map<String, ObjectName> objectNames = new HashMap<>();
    private MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();

    SentryClient getClient(String name) throws Exception {
      if (clients.containsKey(name)) {
        return clients.get(name);
      }
      SentryClient client = new SentryClient(null, null);
      client.setDist(name);

      clients.put(name, client);
      objectNames.put(name, new RegistryClientConfiguration(this, name).register(beanServer, name));
      return client;
    }

    void remove(String name) throws Exception {
      beanServer.unregisterMBean(objectNames.remove(name));
      clients.remove(name);
    }

    private static class RegistryClientConfiguration extends AbstractSentryConfiguration {
      private SentryRegistry registry;
      private String name;

      private RegistryClientConfiguration(
          SentryRegistry registry, String name) {
        this.registry = registry;
        this.name = name;
      }

      @Override
      protected SentryClient getClient() {
        try {
          return registry.getClient(name);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public void reinitialize(String dsn) {
        try {
          registry.remove(name);
          registry.getClient(name);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}

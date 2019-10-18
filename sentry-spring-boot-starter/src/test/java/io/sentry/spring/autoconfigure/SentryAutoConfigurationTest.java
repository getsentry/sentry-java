package io.sentry.spring.autoconfigure;

import io.sentry.SentryClient;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.test.util.EnvironmentTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class SentryAutoConfigurationTest {

    private AnnotationConfigEmbeddedWebApplicationContext context;

    @Before
    public void setup() {
        if (this.context == null) {
            this.context = new AnnotationConfigEmbeddedWebApplicationContext();
        }
    }

    @After
    public void teardown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    public void testSentryDefaultAutoConfiguration() {
        load();
        this.context.refresh();
        String[] exceptionResolverBeans = this.context
                .getBeanNamesForType(SentryExceptionResolver.class);
        String[] servletContextInitialerBeans = this.context
                .getBeanNamesForType(SentryServletContextInitializer.class);
        String[] sentryClientBeans = this.context
                .getBeanNamesForType(SentryClient.class);

        assertThat(exceptionResolverBeans).contains("sentryExceptionResolver");
        assertThat(servletContextInitialerBeans).contains("sentryServletContextInitializer");
        assertThat(sentryClientBeans).contains("sentryClient");
    }

    @Test
    public void testSentryAutoConfigurationIsEnabled() {
        load();
        EnvironmentTestUtils.addEnvironment(this.context, "sentry.enabled:false");
        this.context.refresh();

        String[] beans = this.context.getBeanNamesForType(SentryExceptionResolver.class);
        assertThat(beans).isEmpty();

        beans = this.context.getBeanNamesForType(SentryClient.class);
        assertThat(beans).isEmpty();
    }

    @Test
    public void testSentryClientIsDisabled() {
        load();
        EnvironmentTestUtils.addEnvironment(this.context, "sentry.init-default-client:false");
        this.context.refresh();

        String[] beans = this.context.getBeanNamesForType(SentryExceptionResolver.class);
        assertThat(beans).isNotEmpty();

        beans = this.context.getBeanNamesForType(SentryClient.class);
        assertThat(beans).isEmpty();
    }

    @Test
    public void testSentryClientConfig() {
        load();
        EnvironmentTestUtils.addEnvironment(this.context,
                "sentry.dsn:https://00059966e6224d03a77ea5eca10fbe18@sentry.mycompany.com/14",
                "sentry.release:1.0.1",
                "sentry.dist:x86",
                "sentry.environment:staging",
                "sentry.serverName:megaServer",
                "sentry.tags.firstTag:Hello",
                "sentry.mdcTags:mdcTagA",
                "sentry.extra.extraTag:extra");
        this.context.refresh();

        String[] beans = this.context.getBeanNamesForType(SentryClient.class);
        assertThat(beans).isNotEmpty();

        SentryClient sentryClient = this.context.getBean(SentryClient.class);

        assertThat(sentryClient.getRelease()).isEqualTo("1.0.1");
        assertThat(sentryClient.getDist()).isEqualTo("x86");
        assertThat(sentryClient.getEnvironment()).isEqualTo("staging");
        assertThat(sentryClient.getServerName()).isEqualTo("megaServer");
        assertThat(sentryClient.getTags()).isNotEmpty().containsKey("firstTag");
        assertThat(sentryClient.getMdcTags()).isNotEmpty().contains("mdcTagA");
        assertThat(sentryClient.getExtra()).isNotEmpty().containsKey("extraTag");
    }

    private void load() {
        this.context.register(EmbeddedServletContainerAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class,
                PropertyPlaceholderAutoConfiguration.class,
                SentryAutoConfiguration.class);
    }

}

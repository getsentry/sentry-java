package io.sentry.spring.autoconfigure;

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
	public void testSentryExceptionResolverIsAvailable() {
		load();
		this.context.refresh();
		String[] exceptionResolverBeans = this.context
				.getBeanNamesForType(SentryExceptionResolver.class);
		String[] servletContextInitialerBeans = this.context
				.getBeanNamesForType(SentryServletContextInitializer.class);

		assertThat(exceptionResolverBeans).contains("sentryExceptionResolver");
		assertThat(servletContextInitialerBeans)
				.contains("sentryServletContextInitializer");
	}

	@Test
	public void testSentryAutoConfigurationIsEnabled() {
		load();
		EnvironmentTestUtils.addEnvironment(this.context, "sentry.enabled:false");
		this.context.refresh();
		String[] beans = this.context.getBeanNamesForType(SentryExceptionResolver.class);
		assertThat(beans).isEmpty();
	}

	private void load() {
		this.context.register(EmbeddedServletContainerAutoConfiguration.class,
				HttpMessageConvertersAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class,
				SentryAutoConfiguration.class);
	}

}

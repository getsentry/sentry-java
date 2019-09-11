package io.sentry.spring.autoconfigure;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.connection.*;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.Event;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.spring.SentryExceptionResolver;
import io.sentry.spring.SentryServletContextInitializer;
import io.sentry.time.FixedClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.test.util.EnvironmentTestUtils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SentryAutoConfigurationTest {

	private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
	private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";
	private static final Date FIXED_DATE = new Date(1483228800L);

	private AnnotationConfigEmbeddedWebApplicationContext context;
	private static AtomicBoolean shouldEventcalled = new AtomicBoolean(false);
	private static AtomicBoolean sendEventCalled = new AtomicBoolean(false);



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

	@Test
	public void testSentryInitEventSendCallbacks() {
		load();
		FixedClock fixedClock = new FixedClock(FIXED_DATE);
		LockdownManager lockdownManager = mock(LockdownManager.class, withSettings()
				.useConstructor(fixedClock)
				.defaultAnswer(CALLS_REAL_METHODS));
		Connection mockConnection = mock(AbstractConnection.class, withSettings()
				.useConstructor(publicKey, secretKey, lockdownManager)
				.defaultAnswer(CALLS_REAL_METHODS));
		ContextManager contextManager = new SingletonContextManager();
		SentryClient sentryClient = new SentryClient(mockConnection, contextManager);
		Sentry.setStoredClient(sentryClient);
		this.context.refresh();
		Event mockEvent = mock(Event.class);
		sentryClient.sendEvent(mockEvent);
		assertThat(shouldEventcalled.get()).isTrue();
		assertThat(sendEventCalled.get()).isTrue();
	}

	private void load() {
		this.context.register(EmbeddedServletContainerAutoConfiguration.class,
				HttpMessageConvertersAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class,
				SentryAutoConfiguration.class,
				TestShouldSendEventCallback.class,
				TestEventSendCallback.class);
	}

	private static class TestShouldSendEventCallback implements ShouldSendEventCallback{

		@Override
		public boolean shouldSend(Event event) {
			shouldEventcalled.set(true);
			return true;
		}
	}

	private static class TestEventSendCallback implements EventSendCallback {


		@Override
		public void onFailure(Event event, Exception exception) {

		}

		@Override
		public void onSuccess(Event event) {
			sendEventCalled.set(true);
		}
	}
}

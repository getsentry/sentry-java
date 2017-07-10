package io.sentry.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.interfaces.*;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.Verifications;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.hamcrest.Matchers;
import org.slf4j.MarkerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SentryAppenderEventBuildingTest extends BaseTest {
    @Tested
    private SentryAppender sentryAppender = null;
    @Injectable
    private SentryClient mockSentryClient = null;
    @Injectable
    private Context mockContext = null;
    private String mockExtraTag = "60f42409-c029-447d-816a-fb2722913c93";
    private String mockMinLevel = "ALL";
    private Set<String> extraTags;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        sentryAppender.setContext(mockContext);
        sentryAppender.setMinLevel(mockMinLevel);
        extraTags = new HashSet<>();
        extraTags.add(mockExtraTag);

        new NonStrictExpectations() {{
            final BasicStatusManager statusManager = new BasicStatusManager();
            final OnConsoleStatusListener listener = new OnConsoleStatusListener();
            listener.start();
            statusManager.add(listener);

            mockContext.getStatusManager();
            result = statusManager;
        }};
    }

    private void assertNoErrorsInStatusManager() throws Exception {
        assertThat(mockContext.getStatusManager().getCount(), is(0));
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String message = "14a667c5-0de3-4b43-b62b-f9ccced7adf1";
        final String loggerName = "2cc053ad-8c13-44a6-849f-11a9bf8ba646";
        final String threadName = "a70e658d-f5fa-4707-b7ac-0d503429f1dd";
        final Date date = new Date(1373883196416L);

        ILoggingEvent loggingEvent = new MockUpLoggingEvent(loggerName, null, Level.INFO, message, null, null, null,
                threadName, null, date.getTime()).getMockInstance();
        sentryAppender.append(loggingEvent);

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
            assertThat(event.getSdk().getIntegrations(), contains("logback"));
        }};
        assertNoErrorsInStatusManager();
    }

    @DataProvider(name = "levels")
    private Object[][] levelConversions() {
        return new Object[][]{
                {Event.Level.DEBUG, Level.TRACE},
                {Event.Level.DEBUG, Level.DEBUG},
                {Event.Level.INFO, Level.INFO},
                {Event.Level.WARNING, Level.WARN},
                {Event.Level.ERROR, Level.ERROR}};
    }

    @Test(dataProvider = "levels")
    public void testLevelConversion(final Event.Level expectedLevel, Level level) throws Exception {
        sentryAppender.append(new MockUpLoggingEvent(null, null, level, null, null, null).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.ERROR, null, null, exception).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                    .get(ExceptionInterface.EXCEPTION_INTERFACE);
            SentryException sentryException = exceptionInterface.getExceptions().getFirst();
            assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
            assertThat(sentryException.getStackTraceInterface().getStackTrace(),
                is(SentryStackTraceElement.fromStackTraceElements(exception.getStackTrace(), null)));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testLogParametrisedMessage() throws Exception {
        final String messagePattern = "Formatted message {} {} {}";
        final Object[] parameters = {"first parameter", new Object[0], null};

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, messagePattern, parameters, null)
                .getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            MessageInterface messageInterface = (MessageInterface) event.getSentryInterfaces()
                    .get(MessageInterface.MESSAGE_INTERFACE);

            assertThat(event.getMessage(), is("Formatted message first parameter [] null"));
            assertThat(messageInterface.getMessage(), is(messagePattern));
            assertThat(messageInterface.getParameters(),
                    is(Arrays.asList(parameters[0].toString(), parameters[1].toString(), null)));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testMarkerAddedToTag() throws Exception {
        final String markerName = "d33e3927-ea6c-4a5a-b66c-8dcb2052e812";

        sentryAppender.append(
                new MockUpLoggingEvent(null, MarkerFactory.getMarker(markerName), Level.INFO, null, null, null)
                        .getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getTags(), Matchers.<String, Object>hasEntry(SentryAppender.LOGBACK_MARKER, markerName));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        final String extraKey = "10e09b11-546f-4c57-99b2-cf3c627c8737";
        final String extraValue = "5f7a53b1-4354-4120-a368-78a615705540";

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                Collections.singletonMap(extraKey, extraValue), null, null, 0).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testContextPropertiesAddedToExtra() throws Exception {
        final String extraKey = "0489bc59-b4ba-4890-9a60-58e65624fe8c";
        final String extraValue = "986adaa7-c0e4-4c09-9c5e-49edaf2e6d53";

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                null, null, null, 0, Collections.singletonMap(extraKey, extraValue)).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testMdcTakesPrecedenceOverContextProperties() throws Exception {
        final String mdcKey = "0aab006e-0128-42d7-84d5-aa88329beb19";
        final String mdcValue = "8ba43697-7568-40e2-914b-4d2c3f12e70e";
        final String contextKey = mdcKey;
        final String contextValue = "66d123eb-7786-4f3d-86f1-a906039401d9";

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                Collections.singletonMap(mdcKey, mdcValue), null, null, 0,
                Collections.singletonMap(contextKey, contextValue)).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(mdcKey, mdcValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testSourceUsedAsStacktrace() throws Exception {
        final StackTraceElement[] location = {new StackTraceElement("854de9b9-95ea-4dae-8e01-23b25c9bd271",
                "49974348-1704-47cc-be5a-4e72f2e0db33",
                "bf48ef03-657c-4924-844a-317743c4599b", 42)};

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null, null, null, location, 0)
                .getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            StackTraceInterface stackTraceInterface = (StackTraceInterface) event.getSentryInterfaces()
                    .get(StackTraceInterface.STACKTRACE_INTERFACE);
            assertThat(stackTraceInterface.getStackTrace(), is(SentryStackTraceElement.fromStackTraceElements(location, null)));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testCulpritWithSource() throws Exception {
        final StackTraceElement[] location = {new StackTraceElement("a", "b", "c", 42),
                new StackTraceElement("d", "e", "f", 69)};

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null, null, null, location, 0)
                .getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getCulprit(), is("a.b(c:42)"));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        final String loggerName = "ee1c33e8-2f2e-4613-9c2c-2564624a9d4f";

        sentryAppender.append(new MockUpLoggingEvent(loggerName, null, Level.INFO, null, null, null).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getCulprit(), is(loggerName));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testExtraTagObtainedFromMdc() throws Exception {
        Map<String, String> mdcPropertyMap = new HashMap<>();
        mdcPropertyMap.put(mockExtraTag, "47008f35-50c8-4e40-94ca-c8c1a3ddb729");
        mdcPropertyMap.put("other_property", "cb9c92a1-0182-4e9c-866f-b06b271cd196");

        new NonStrictExpectations() {{
            mockSentryClient.getMdcTags();
            result = extraTags;
        }};

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null, mdcPropertyMap, null,
                null, 0).getMockInstance());

        new Verifications() {{
            EventBuilder eventBuilder;
            mockSentryClient.sendEvent(eventBuilder = withCapture());
            Event event = eventBuilder.build();
            assertThat(event.getTags().entrySet(), hasSize(1));
            assertThat(event.getTags(), hasEntry(mockExtraTag, "47008f35-50c8-4e40-94ca-c8c1a3ddb729"));
            assertThat(event.getExtra(), not(hasKey(mockExtraTag)));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry("other_property", "cb9c92a1-0182-4e9c-866f-b06b271cd196"));
        }};
        assertNoErrorsInStatusManager();
    }
}

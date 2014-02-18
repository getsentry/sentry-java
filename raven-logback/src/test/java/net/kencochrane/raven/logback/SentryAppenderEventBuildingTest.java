package net.kencochrane.raven.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.BasicStatusManager;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.SentryException;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.hamcrest.Matchers;
import org.slf4j.MarkerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryAppenderEventBuildingTest {
    private SentryAppender sentryAppender;
    @Injectable
    private Raven mockRaven = null;
    @Injectable
    private Context mockContext = null;

    @BeforeMethod
    public void setUp() throws Exception {
        new MockUpStatusPrinter();
        sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setContext(mockContext);
        sentryAppender.initRaven();

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
        final String message = UUID.randomUUID().toString();
        final String loggerName = UUID.randomUUID().toString();
        final String threadName = UUID.randomUUID().toString();
        final Date date = new Date(1373883196416L);

        ILoggingEvent loggingEvent = new MockUpLoggingEvent(loggerName, null, Level.INFO, message, null, null, null,
                threadName, null, date.getTime()).getMockInstance();
        sentryAppender.append(loggingEvent);

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
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
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.ERROR, null, null, exception).getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                    .get(ExceptionInterface.EXCEPTION_INTERFACE);
            SentryException sentryException = exceptionInterface.getExceptions().getFirst();
            assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
            assertThat(sentryException.getStackTraceInterface().getStackTrace(), is(exception.getStackTrace()));
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
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
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
        final String markerName = UUID.randomUUID().toString();

        sentryAppender.append(
                new MockUpLoggingEvent(null, MarkerFactory.getMarker(markerName), Level.INFO, null, null, null)
                        .getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getTags(), Matchers.<String, Object>hasEntry(SentryAppender.LOGBACK_MARKER, markerName));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        final String extraKey = UUID.randomUUID().toString();
        final String extraValue = UUID.randomUUID().toString();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                Collections.singletonMap(extraKey, extraValue), null, null, 0).getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testContextPropertiesAddedToExtra() throws Exception {
        final String extraKey = UUID.randomUUID().toString();
        final String extraValue = UUID.randomUUID().toString();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                null, null, null, 0, Collections.singletonMap(extraKey, extraValue)).getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testMdcTakesPrecedenceOverContextProperties() throws Exception {
        final String mdcKey = UUID.randomUUID().toString();
        final String mdcValue = UUID.randomUUID().toString();
        final String contextKey = mdcKey;
        final String contextValue = UUID.randomUUID().toString();

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null,
                Collections.singletonMap(mdcKey, mdcValue), null, null, 0,
                Collections.singletonMap(contextKey, contextValue)).getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(mdcKey, mdcValue));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testSourceUsedAsStacktrace() throws Exception {
        final StackTraceElement[] location = {new StackTraceElement(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 42)};

        sentryAppender.append(new MockUpLoggingEvent(null, null, Level.INFO, null, null, null, null, null, location, 0)
                .getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            StackTraceInterface stackTraceInterface = (StackTraceInterface) event.getSentryInterfaces()
                    .get(StackTraceInterface.STACKTRACE_INTERFACE);
            assertThat(stackTraceInterface.getStackTrace(), is(location));
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
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is("a.b(c:42)"));
        }};
        assertNoErrorsInStatusManager();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        final String loggerName = UUID.randomUUID().toString();

        sentryAppender.append(new MockUpLoggingEvent(loggerName, null, Level.INFO, null, null, null).getMockInstance());

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is(loggerName));
        }};
        assertNoErrorsInStatusManager();
    }
}

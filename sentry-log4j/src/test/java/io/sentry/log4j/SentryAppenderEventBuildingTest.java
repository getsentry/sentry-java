package io.sentry.log4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(JUnitParamsRunner.class)
public class SentryAppenderEventBuildingTest extends BaseTest {
    private SentryAppender sentryAppender;
    private ErrorCounter errorCounter;
    private SentryClient mockSentryClient;
    private String mockExtraTag = "a8e0ad33-3c11-4899-b8c7-c99926c6d7b8";
    private Set<String> extraTags;
    private Logger fakeLogger;

    @Before
    public void setUp() throws Exception {
        mockSentryClient = mock(SentryClient.class);
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        errorCounter = new ErrorCounter();
        sentryAppender.setErrorHandler(errorCounter.getErrorHandler());
        extraTags = new HashSet<>();
        extraTags.add(mockExtraTag);
        sentryAppender.activateOptions();

        fakeLogger = new Logger(null) {};
    }

    private void assertNoErrorsInErrorHandler() throws Exception {
        assertThat(errorCounter.getErrorCount(), is(0));
    }

    private Event verifySendEventCalled() {
        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        return eventBuilderArgumentCaptor.getValue().build();
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = "cdc23028-9b1f-485d-90bf-853d2f5f52d6";
        final String message = "fae94de0-0df3-4f96-92d5-a3fb66e714f3";
        final String threadName = "78ecbf4d-aa61-4dd7-8ef4-b1c49232e8f4";
        final Date date = new Date(1373883196416L);

        fakeLogger = new Logger(loggerName) {};

        sentryAppender.append(new LoggingEvent(null, fakeLogger, date.getTime(), Level.INFO, message, threadName,
                null, null, null, null));

        Event event = verifySendEventCalled();
        assertThat(event.getMessage(), is(message));
        assertThat(event.getLogger(), is(loggerName));
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
        assertThat(event.getTimestamp(), is(date));
        assertThat(event.getSdk().getIntegrations(), contains("log4j"));
        assertNoErrorsInErrorHandler();
    }

    @NamedParameters("levels")
    public Object[][] levelConversions() {
        return new Object[][]{
                {Event.Level.DEBUG, Level.TRACE},
                {Event.Level.DEBUG, Level.DEBUG},
                {Event.Level.INFO, Level.INFO},
                {Event.Level.WARNING, Level.WARN},
                {Event.Level.ERROR, Level.ERROR},
                {Event.Level.FATAL, Level.FATAL}};
    }

    @Test
    @Parameters(named = "levels")
    public void testLevelConversion(Event.Level expectedLevel, Level level) throws Exception {
        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, level, null, null));

        Event event = verifySendEventCalled();
        assertThat(event.getLevel(), is(expectedLevel));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception("027a0db3-fb98-4377-bafb-fe5a49f067e8");

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, exception));

        Event event = verifySendEventCalled();
        ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                .get(ExceptionInterface.EXCEPTION_INTERFACE);
        SentryException sentryException = exceptionInterface.getExceptions().getFirst();
        assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
        assertThat(sentryException.getStackTraceInterface().getStackTrace(),
            is(SentryStackTraceElement.fromStackTraceElements(exception.getStackTrace(), null)));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        final String extraKey = "1aeb7253-6e0d-4902-86d6-7e4b36571cfd";
        final String extraValue = "b2e19866-08a2-4611-b72c-150fa6aa3394";

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, null,
                null, null, null, Collections.singletonMap(extraKey, extraValue)));

        Event event = verifySendEventCalled();
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testNdcAddedToExtra() throws Exception {
        final String ndcEntries = "930580ba-f92f-4893-855b-ac24efa1a6c2 fa32ad74-a015-492a-991f-c6a0e04accaf be9dd914-3690-4781-97b2-fe14aedb4cbd";

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, null,
                null, ndcEntries, null, null));

        Event event = verifySendEventCalled();
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_NDC, ndcEntries));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testSourceUsedAsStacktrace() throws Exception {
        final String className = "8004ac1e-8bd3-4762-abe0-2d0d79ae4e40";
        final String methodName = "ce7cd195-9e6d-4315-b883-12951be3da6e";
        final String fileName = "1ab50f43-f11c-4439-a05c-d089281411fa";
        final int line = 42;
        LocationInfo locationInfo = new LocationInfo(fileName, className, methodName, Integer.toString(line));

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, null,
                null, null, locationInfo, null));

        Event event = verifySendEventCalled();
        StackTraceInterface stackTraceInterface = (StackTraceInterface) event.getSentryInterfaces()
                .get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface.getStackTrace(), arrayWithSize(1));
        assertThat(stackTraceInterface.getStackTrace()[0],
                is(new SentryStackTraceElement(className, methodName, fileName, line, null, null, null, null)));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExtraTagObtainedFromMdc() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(mockExtraTag, "ac84f38a-3889-41ed-9519-201402688abb");
        properties.put("other_property", "10ebc4f6-a915-46d0-bb60-75bc9bd71371");

        when(mockSentryClient.getMdcTags()).thenReturn(extraTags);

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, null, null, null, null, properties));

        Event event = verifySendEventCalled();
        assertThat(event.getTags().entrySet(), hasSize(1));
        assertThat(event.getTags(), hasEntry(mockExtraTag, "ac84f38a-3889-41ed-9519-201402688abb"));
        assertThat(event.getExtra(), not(hasKey(mockExtraTag)));
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry("other_property", "10ebc4f6-a915-46d0-bb60-75bc9bd71371"));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExtraTagObtainedFromMdcConvertedToString() throws Exception {
        Map<String, Object> properties = Collections.singletonMap(mockExtraTag, (Object) "3c8981b4-01ad-47ec-8a3a-77a0bbcb42e2");
        when(mockSentryClient.getMdcTags()).thenReturn(extraTags);

        sentryAppender.append(new LoggingEvent(null, fakeLogger, 0, Level.ERROR, null, null, null, null, null, properties));

        Event event = verifySendEventCalled();
        assertThat(event.getTags().entrySet(), hasSize(1));
        assertThat(event.getTags(), hasEntry(mockExtraTag, "3c8981b4-01ad-47ec-8a3a-77a0bbcb42e2"));
        assertThat(event.getExtra(), not(hasKey(mockExtraTag)));
        assertNoErrorsInErrorHandler();
    }
}

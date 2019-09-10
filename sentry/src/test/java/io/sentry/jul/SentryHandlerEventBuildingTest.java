package io.sentry.jul;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.interfaces.*;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitParamsRunner.class)
public class SentryHandlerEventBuildingTest extends BaseTest {
    private SentryHandler sentryHandler = null;
    private ErrorManager errorManager = null;
    private SentryClient mockSentryClient = null;

    @Before
    public void setup() {
        mockSentryClient = mock(SentryClient.class);
        errorManager = mock(ErrorManager.class);
        Sentry.setStoredClient(mockSentryClient);
        sentryHandler = new SentryHandler();
        sentryHandler.setErrorManager(errorManager);
    }

    private void assertNoErrorsInErrorManager() throws Exception {
        verify(errorManager, never()).error(anyString(), any(Exception.class), anyInt());
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = "e9cb78a9-aec8-4fcd-8580-42b428653061";
        final String message = "1feb7133-1bf5-4982-a30d-44883aa3de9c";
        final Object[] arguments = {"341ecbc9-3d0a-4799-9ff9-bdd18bb2b399", "acbb0393-57a8-4fff-a8b7-bb391867628c"};
        final Date date = new Date(1373883196416L);
        final long threadId = 12;

        sentryHandler.setLevel(Level.INFO);
        sentryHandler.publish(newLogRecord(loggerName, Level.INFO, message, arguments, null, null, threadId, date.getTime()));

        ArgumentCaptor<EventBuilder> eventBuilderCaptor = ArgumentCaptor.forClass(EventBuilder.class);

        verify(mockSentryClient).sendEvent(eventBuilderCaptor.capture());
        Event event = eventBuilderCaptor.getValue().build();
        assertThat(event.getMessage(), is(message));
        Map<String, SentryInterface> sentryInterfaces = event.getSentryInterfaces();
        assertThat(sentryInterfaces, hasKey(MessageInterface.MESSAGE_INTERFACE));
        MessageInterface messageInterface = (MessageInterface) sentryInterfaces.get(MessageInterface.MESSAGE_INTERFACE);
        assertThat(messageInterface.getParameters(), contains(arguments));
        assertThat(event.getLogger(), is(loggerName));
        assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryHandler.THREAD_ID, (int) threadId));
        assertThat(event.getTimestamp(), is(date));
        assertThat(event.getSdk().getIntegrations(), contains("java.util.logging"));

        assertNoErrorsInErrorManager();
    }

    @NamedParameters("levelConversions")
    private Object[][] levelConversions() {
        return new Object[][]{
                {Event.Level.DEBUG, Level.FINEST},
                {Event.Level.DEBUG, Level.FINER},
                {Event.Level.DEBUG, Level.FINE},
                {Event.Level.DEBUG, Level.CONFIG},
                {Event.Level.INFO, Level.INFO},
                {Event.Level.WARNING, Level.WARNING},
                {Event.Level.ERROR, Level.SEVERE}};
    }

    @Test
    @Parameters(named = "levelConversions")
    public void testLevelConversion(final Event.Level expectedLevel, Level level) throws Exception {
        sentryHandler.setLevel(Level.ALL);
        sentryHandler.publish(newLogRecord(null, level, null, null, null));

        ArgumentCaptor<EventBuilder> eventBuilderCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderCaptor.capture());
        Event event = eventBuilderCaptor.getValue().build();
        assertThat(event.getLevel(), is(expectedLevel));
        assertNoErrorsInErrorManager();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception("c2712792-e1ef-4824-a0e1-0e3e22907661");

        sentryHandler.publish(newLogRecord(null, Level.SEVERE, null, null, exception));

        ArgumentCaptor<EventBuilder> eventBuilderCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderCaptor.capture());
        Event event = eventBuilderCaptor.getValue().build();
        ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                .get(ExceptionInterface.EXCEPTION_INTERFACE);
        SentryException sentryException = exceptionInterface.getExceptions().getFirst();
        assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
        assertThat(sentryException.getStackTraceInterface().getStackTrace(),
            is(SentryStackTraceElement.fromStackTraceElements(exception.getStackTrace(), null)));
        assertNoErrorsInErrorManager();
    }

    private LogRecord newLogRecord(String loggerName, Level level, String message,
                                   Object[] argumentArray, Throwable t) {
        return newLogRecord(loggerName, level, message, argumentArray, t, null,
                Thread.currentThread().getId(), System.currentTimeMillis());
    }

    private LogRecord newLogRecord(String loggerName, Level level, String message, Object[] argumentArray,
                                   Throwable throwable, StackTraceElement[] callerData, long threadId, long timestamp) {
        LogRecord logRecord = new LogRecord(level, message);
        logRecord.setLoggerName(loggerName);
        logRecord.setParameters(argumentArray);
        logRecord.setThrown(throwable);
        logRecord.setMillis(timestamp);
        logRecord.setThreadID((int) threadId);
        if (callerData != null && callerData.length > 0) {
            logRecord.setSourceClassName(callerData[0].getClassName());
            logRecord.setSourceMethodName(callerData[0].getMethodName());
        }
        return logRecord;
    }
}

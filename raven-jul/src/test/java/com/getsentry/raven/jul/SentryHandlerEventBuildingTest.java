package com.getsentry.raven.jul;

import com.getsentry.raven.environment.RavenEnvironment;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import com.getsentry.raven.Raven;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.interfaces.ExceptionInterface;
import com.getsentry.raven.event.interfaces.MessageInterface;
import com.getsentry.raven.event.interfaces.SentryException;
import com.getsentry.raven.event.interfaces.SentryInterface;
import org.hamcrest.Matchers;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SentryHandlerEventBuildingTest {
    @Tested
    private SentryHandler sentryHandler = null;
    @Injectable
    private ErrorManager errorManager = null;
    @Injectable
    private Raven mockRaven = null;

    private void assertNoErrorsInErrorManager() throws Exception {
        new Verifications() {{
            errorManager.error(anyString, (Exception) any, anyInt);
            times = 0;
        }};
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = "e9cb78a9-aec8-4fcd-8580-42b428653061";
        final String message = "1feb7133-1bf5-4982-a30d-44883aa3de9c";
        final Object[] arguments = {"341ecbc9-3d0a-4799-9ff9-bdd18bb2b399", "acbb0393-57a8-4fff-a8b7-bb391867628c"};
        final Date date = new Date(1373883196416L);
        final long threadId = 12;

        sentryHandler.publish(newLogRecord(loggerName, Level.INFO, message, arguments, null, null, threadId, date.getTime()));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            Map<String, SentryInterface> sentryInterfaces = event.getSentryInterfaces();
            assertThat(sentryInterfaces, hasKey(MessageInterface.MESSAGE_INTERFACE));
            MessageInterface messageInterface = (MessageInterface) sentryInterfaces.get(MessageInterface.MESSAGE_INTERFACE);
            assertThat(messageInterface.getParameters(), contains(arguments));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryHandler.THREAD_ID, (int) threadId));
            assertThat(event.getTimestamp(), is(date));
            assertThat(event.getSdkName(), is(RavenEnvironment.SDK_NAME + ":jul"));
        }};
        assertNoErrorsInErrorManager();
    }

    @DataProvider(name = "levels")
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

    @Test(dataProvider = "levels")
    public void testLevelConversion(final Event.Level expectedLevel, Level level) throws Exception {
        sentryHandler.publish(newLogRecord(null, level, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertNoErrorsInErrorManager();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception("c2712792-e1ef-4824-a0e1-0e3e22907661");

        sentryHandler.publish(newLogRecord(null, Level.SEVERE, null, null, exception));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                    .get(ExceptionInterface.EXCEPTION_INTERFACE);
            final SentryException sentryException = exceptionInterface.getExceptions().getFirst();
            assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
            assertThat(sentryException.getStackTraceInterface().getStackTrace(), is(exception.getStackTrace()));
        }};
        assertNoErrorsInErrorManager();
    }

    @Test
    public void testCulpritWithSource() throws Exception {
        final String className = "a";
        final String methodName = "b";
        final StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, null, 0);

        sentryHandler.publish(newLogRecord(null, Level.SEVERE, null, null, null,
                new StackTraceElement[]{stackTraceElement}, 0, 0));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is("a.b"));
        }};
        assertNoErrorsInErrorManager();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        final String loggerName = "0c929a2e-f2bc-4ebb-ad41-a29fb1591ffe";

        sentryHandler.publish(newLogRecord(loggerName, Level.SEVERE, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is(loggerName));
        }};
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

    @Test
    public void testReleaseAddedToEvent() throws Exception {
        final String release = "d7b4a6a0-1a0a-4381-a519-e2ccab609003";
        sentryHandler.setRelease(release);

        sentryHandler.publish(newLogRecord(null, Level.INFO, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getRelease(), is(release));
        }};
        assertNoErrorsInErrorManager();
    }

    @Test
    public void testEnvironmentAddedToEvent() throws Exception {
        final String environment = "d7b4a6a0-1a0a-4381-a519-e2ccab609003";
        sentryHandler.setEnvironment(environment);

        sentryHandler.publish(newLogRecord(null, Level.INFO, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getEnvironment(), is(environment));
        }};
        assertNoErrorsInErrorManager();
    }

}

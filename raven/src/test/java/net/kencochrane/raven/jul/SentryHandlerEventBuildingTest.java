package net.kencochrane.raven.jul;

import mockit.Injectable;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.UUID;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SentryHandlerEventBuildingTest {
    private SentryHandler sentryHandler;
    @Injectable
    private ErrorManager errorManager = null;
    @Injectable
    private Raven mockRaven = null;

    @BeforeMethod
    public void setUp() throws Exception {
        sentryHandler = new SentryHandler(mockRaven);
        sentryHandler.setErrorManager(errorManager);
    }

    private void assertNoErrorsInErrorManager() throws Exception {
        new Verifications() {{
            errorManager.error(anyString, (Exception) any, anyInt);
            times = 0;
        }};
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = UUID.randomUUID().toString();
        final String message = UUID.randomUUID().toString();
        final Date date = new Date(1373883196416L);
        final long threadId = 12;

        sentryHandler.publish(newLogRecord(loggerName, Level.INFO, message, null, null, null, threadId, date.getTime()));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers((EventBuilder) any);
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryHandler.THREAD_ID, (int) threadId));
            assertThat(event.getTimestamp(), is(date));
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
        final Exception exception = new Exception(UUID.randomUUID().toString());

        sentryHandler.publish(newLogRecord(null, Level.SEVERE, null, null, exception));

        new Verifications() {{
            Event event;
            Throwable throwable;
            mockRaven.sendEvent(event = withCapture());
            ExceptionInterface exceptionInterface = (ExceptionInterface) event.getSentryInterfaces()
                    .get(ExceptionInterface.EXCEPTION_INTERFACE);
            throwable = exceptionInterface.getThrowable();
            assertThat(throwable.getMessage(), is(exception.getMessage()));
            assertThat(throwable.getStackTrace(), is(exception.getStackTrace()));
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
        final String loggerName = UUID.randomUUID().toString();

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
}

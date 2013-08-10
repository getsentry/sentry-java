package net.kencochrane.raven.log4j;

import com.google.common.base.Joiner;
import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.RavenFactory;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;

public class SentryAppenderTest {
    private SentryAppender sentryAppender;
    @Mocked
    private Raven mockRaven = null;
    @Injectable
    private Logger mockLogger = null;
    @Injectable
    private ErrorHandler mockErrorHandler = null;

    @BeforeMethod
    public void setUp() {
        sentryAppender = new SentryAppender(mockRaven);
        sentryAppender.setErrorHandler(mockErrorHandler);
    }

    @Test
    public void testSimpleMessageLogging() throws Exception {
        final String loggerName = UUID.randomUUID().toString();
        final String message = UUID.randomUUID().toString();
        final String threadName = UUID.randomUUID().toString();
        final Date date = new Date(1373883196416L);
        new Expectations() {{
            onInstance(mockLogger).getName();
            result = loggerName;
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, date.getTime(), Level.INFO, message, threadName,
                null, null, null, null));

        new Verifications() {{
            Event event;
            mockRaven.runBuilderHelpers(withAny(new EventBuilder()));
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getMessage(), is(message));
            assertThat(event.getLogger(), is(loggerName));
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
            assertThat(event.getTimestamp(), is(date));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testLevelConversion() throws Exception {
        assertLevelConverted(Event.Level.DEBUG, Level.TRACE);
        assertLevelConverted(Event.Level.DEBUG, Level.DEBUG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARN);
        assertLevelConverted(Event.Level.ERROR, Level.ERROR);
        assertLevelConverted(Event.Level.FATAL, Level.FATAL);
    }

    private void assertLevelConverted(final Event.Level expectedLevel, Level level) throws Exception {
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, level, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getLevel(), is(expectedLevel));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testExceptionLogging() throws Exception {
        final Exception exception = new Exception(UUID.randomUUID().toString());

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, exception));

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
        assertDoNotGenerateErrors();
    }

    @Test
    public void testMdcAddedToExtra() throws Exception {
        final String extraKey = UUID.randomUUID().toString();
        final String extraValue = UUID.randomUUID().toString();

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, null, Collections.singletonMap(extraKey, extraValue)));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testNdcAddedToExtra() throws Exception {
        final String ndcEntries = Joiner.on(' ').join(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, ndcEntries, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_NDC, ndcEntries));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testSourceUsedAsStacktrace(@Injectable @NonStrict final LocationInfo locationInfo) throws Exception {
        final String className = UUID.randomUUID().toString();
        final String methodName = UUID.randomUUID().toString();
        final String fileName = UUID.randomUUID().toString();
        final int line = 42;
        new Expectations() {{
            locationInfo.getClassName();
            result = className;
            locationInfo.getMethodName();
            result = methodName;
            locationInfo.getFileName();
            result = fileName;
            locationInfo.getLineNumber();
            result = Integer.toString(line);
            setField(locationInfo, "fullInfo", "");
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, locationInfo, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            StackTraceInterface stackTraceInterface = (StackTraceInterface) event.getSentryInterfaces()
                    .get(StackTraceInterface.STACKTRACE_INTERFACE);
            assertThat(stackTraceInterface.getStackTrace(), arrayWithSize(1));
            assertThat(stackTraceInterface.getStackTrace()[0],
                    is(new StackTraceElement(className, methodName, fileName, line)));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testCulpritWithSource(@Injectable @NonStrict final LocationInfo locationInfo) throws Exception {
        final String className = "a";
        final String methodName = "b";
        final String fileName = "c";
        final int line = 42;
        new Expectations() {{
            locationInfo.getClassName();
            result = className;
            locationInfo.getMethodName();
            result = methodName;
            locationInfo.getFileName();
            result = fileName;
            locationInfo.getLineNumber();
            result = Integer.toString(line);
            setField(locationInfo, "fullInfo", "");
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null,
                null, null, locationInfo, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is("a.b(c:42)"));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testCulpritWithoutSource() throws Exception {
        final String loggerName = UUID.randomUUID().toString();
        new Expectations() {{
            onInstance(mockLogger).getName();
            result = loggerName;
        }};

        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.ERROR, null, null));

        new Verifications() {{
            Event event;
            mockRaven.sendEvent(event = withCapture());
            assertThat(event.getCulprit(), is(loggerName));
        }};
        assertDoNotGenerateErrors();
    }

    @Test
    public void testCorrectRavenInstanceUsedIfNotProvided() throws Exception {
        final SentryAppender sentryAppender = new SentryAppender();

        new NonStrictExpectations() {
            @Mocked
            private final Dsn dsn = null;
            @Mocked
            private RavenFactory ravenFactory = null;

            {
                RavenFactory.ravenInstance(dsn, anyString);
                returns(mockRaven);
            }
        };

        sentryAppender.activateOptions();
        sentryAppender.append(new LoggingEvent(null, mockLogger, 0, Level.INFO, null, null));

        new Verifications() {{
            onInstance(mockRaven).sendEvent((Event) any);
            assertDoNotGenerateErrors();
        }};
    }

    private void assertDoNotGenerateErrors() throws Exception{
        new Verifications() {{
            mockErrorHandler.error(anyString);
            times = 0;
            mockErrorHandler.error(anyString, (Exception) any, anyInt);
            times = 0;
            mockErrorHandler.error(anyString, (Exception) any, anyInt, (LoggingEvent) any);
            times = 0;
        }};
    }
}

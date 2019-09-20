package io.sentry.log4j2;

import io.sentry.BaseTest;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.*;
import junitparams.JUnitParamsRunner;
import junitparams.NamedParameters;
import junitparams.Parameters;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.DefaultThreadContextStack;
import org.apache.logging.log4j.util.StringMap;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class SentryAppenderEventBuildingTest extends BaseTest {
    private SentryAppender sentryAppender = null;
    private ErrorCounter errorCounter;
    private SentryClient mockSentryClient = null;
    private String mockExtraTag = "d421627f-7a25-4d43-8210-140dfe73ff10";
    private Set<String> extraTags;

    @Before
    public void setUp() {
        mockSentryClient = mock(SentryClient.class);
        Sentry.setStoredClient(mockSentryClient);
        sentryAppender = new SentryAppender();
        errorCounter = new ErrorCounter();
        sentryAppender.setHandler(errorCounter.getErrorHandler());
        extraTags = new HashSet<>();
        extraTags.add(mockExtraTag);
    }

    private void assertNoErrorsInErrorHandler() {
        assertThat(errorCounter.getErrorCount(), is(0));
    }

    @Test
    public void testSimpleMessageLogging() {
        final String loggerName = "0a05c9ff-45ef-45cf-9595-9307b0729a0d";
        final String message = "6ff10df4-2e27-43f5-b4e9-a957f8678176";
        final String threadName = "f891f3c4-c619-4441-9c47-f5c8564d3c0a";
        final Date date = new Date(1373883196416L);

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setLoggerName(loggerName)
            .setThreadName(threadName)
            .setTimeMillis(date.getTime())
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(message))
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat(sentryEvent.getMessage(), is(message));
        assertThat(sentryEvent.getLogger(), is(loggerName));
        assertThat(sentryEvent.getExtra(), Matchers.<String, Object>hasEntry(SentryAppender.THREAD_NAME, threadName));
        assertThat(sentryEvent.getTimestamp(), is(date));
        assertThat(sentryEvent.getSdk().getIntegrations(), contains("log4j2"));
        assertNoErrorsInErrorHandler();
    }

    @NamedParameters("levels")
    private Object[][] levelConversions() {
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
    public void testLevelConversion(final Event.Level expectedLevel, Level level) throws Exception {

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setLevel(level)
            .setMessage(new SimpleMessage(""))
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat(sentryEvent.getLevel(), is(expectedLevel));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExceptionLogging() {
        final Exception exception = new Exception("d0d1b31f-e885-42e3-aac6-48c500f10ed1");

        sentryAppender.append(new Log4jLogEvent(null, null, null, Level.ERROR, new SimpleMessage(""), exception));

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        ExceptionInterface exceptionInterface = (ExceptionInterface) sentryEvent.getSentryInterfaces()
            .get(ExceptionInterface.EXCEPTION_INTERFACE);
        SentryException sentryException = exceptionInterface.getExceptions().getFirst();
        assertThat(sentryException.getExceptionMessage(), is(exception.getMessage()));
        assertThat(sentryException.getStackTraceInterface().getStackTrace(),
            is(SentryStackTraceElement.fromStackTraceElements(exception.getStackTrace(), null)));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testLogParametrisedMessage() {
        final String messagePattern = "Formatted message {} {} {}";
        final Object[] parameters = {"first parameter", new Object[0], null};

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setMessage(new FormattedMessage(messagePattern, parameters))
            .setLevel(Level.INFO)
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        MessageInterface messageInterface = (MessageInterface) sentryEvent.getSentryInterfaces()
            .get(MessageInterface.MESSAGE_INTERFACE);
        assertThat(sentryEvent.getMessage(), is("Formatted message first parameter [] null"));
        assertThat(messageInterface.getMessage(), is(messagePattern));
        assertThat(messageInterface.getParameters(),
            is(Arrays.asList(parameters[0].toString(), parameters[1].toString(), null)));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testMarkerAddedToTag() {
        final String markerName = "c97e1fc0-9fff-41b3-8d0d-c24b54c670bb";

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setMarker(MarkerManager.getMarker(markerName))
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat(sentryEvent.getTags(), Matchers.<String, Object>hasEntry(SentryAppender.LOG4J_MARKER, markerName));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testMdcAddedToExtra() {
        final String extraKey = "a4ce2632-8d9c-471d-8b06-1744be2ae8e9";
        final String extraValue = "6dbeb494-197e-4f57-939a-613e2c16607d";

        StringMap mdc = ContextDataFactory.createContextData();
        mdc.putValue(extraKey, extraValue);

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setContextData(mdc)
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setTimeMillis(0)
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat(sentryEvent.getExtra(), Matchers.<String, Object>hasEntry(extraKey, extraValue));
        assertNoErrorsInErrorHandler();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNdcAddedToExtra() {
        final ThreadContext.ContextStack contextStack = new DefaultThreadContextStack(true);
        contextStack.push("444af01f-fb80-414f-b035-15bdb91cb8b2");
        contextStack.push("a1cb5e08-480a-4b32-b675-212f00c44e05");
        contextStack.push("0aa5db14-1579-46ef-aae2-350d974e7fb8");

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setContextStack(contextStack)
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setTimeMillis(0L)
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat((List<String>) sentryEvent.getExtra().get(SentryAppender.LOG4J_NDC), equalTo(contextStack.asList()));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testSourceUsedAsStacktrace() {
        final StackTraceElement location = new StackTraceElement("7039c1f7-21e3-4134-8ced-524281633224",
            "c68f3af9-1618-4d80-ad1b-ea0701568153", "f87a8821-1c70-44b8-81c3-271d454e4b08", 42);

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setSource(location)
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setTimeMillis(0L)
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        StackTraceInterface stackTraceInterface = (StackTraceInterface) sentryEvent.getSentryInterfaces()
            .get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface.getStackTrace(), arrayWithSize(1));
        assertThat(stackTraceInterface.getStackTrace()[0], is(SentryStackTraceElement.fromStackTraceElement(location)));
        assertNoErrorsInErrorHandler();
    }

    @Test
    public void testExtraTagObtainedFromMdc() {
        StringMap mdc = ContextDataFactory.createContextData();
        mdc.putValue(mockExtraTag, "565940d2-f4a4-42f6-9496-42e3c7c85c43");
        mdc.putValue("other_property", "395856e8-fa1d-474f-8fa9-c062b4886527");

        when(mockSentryClient.getMdcTags()).thenReturn(extraTags);

        Log4jLogEvent event = Log4jLogEvent.newBuilder()
            .setContextData(mdc)
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(""))
            .setTimeMillis(0)
            .build();

        sentryAppender.append(event);

        ArgumentCaptor<EventBuilder> eventBuilderArgumentCaptor = ArgumentCaptor.forClass(EventBuilder.class);
        verify(mockSentryClient).sendEvent(eventBuilderArgumentCaptor.capture());
        Event sentryEvent = eventBuilderArgumentCaptor.getValue().build();
        assertThat(sentryEvent.getTags().entrySet(), hasSize(1));
        assertThat(sentryEvent.getTags(), hasEntry(mockExtraTag, "565940d2-f4a4-42f6-9496-42e3c7c85c43"));
        assertThat(sentryEvent.getExtra(), not(hasKey(mockExtraTag)));
        assertThat(sentryEvent.getExtra(), Matchers.<String, Object>hasEntry("other_property", "395856e8-fa1d-474f-8fa9-c062b4886527"));
        assertNoErrorsInErrorHandler();
    }
}

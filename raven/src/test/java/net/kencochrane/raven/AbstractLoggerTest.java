package net.kencochrane.raven;

import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractLoggerTest {
    @Mock
    private Raven mockRaven;

    @Test
    public void testSimpleMessageLogging() throws Exception {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event event;

        String message = UUID.randomUUID().toString();
        logAnyLevel(message);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();

        assertThat(event.getLogger(), is(getCurrentLoggerName()));
        assertThat(event.getMessage(), is(message));
    }

    protected Raven getMockRaven() {
        return mockRaven;
    }

    public abstract void logAnyLevel(String message);

    public abstract void logAnyLevel(String message, Throwable exception);

    public abstract void logAnyLevel(String message, List<String> parameters);

    public abstract String getCurrentLoggerName();

    @Test
    public abstract void testLogLevelConversions() throws Exception;

    protected void assertLogLevel(Event.Level expectedLevel) {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getLevel(), is(expectedLevel));
        reset(mockRaven);
    }

    @Test
    public void testLogException() throws Exception {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Exception exception = new Exception();
        Event event;

        logAnyLevel("message", exception);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        SentryInterface exceptionInterface = event.getSentryInterfaces().get(ExceptionInterface.EXCEPTION_INTERFACE);
        assertThat(exceptionInterface, instanceOf(ExceptionInterface.class));

        // The object isn't exactly the same, but equals delegates to the actual exception in ImmutableThrowable.
        // This is _BAD_ and shouldn't be done, but it's the best way to do it in this particular case.
        assertThat(((ExceptionInterface) exceptionInterface).getThrowable(), Matchers.<Object>equalTo(exception));


        SentryInterface stackTraceInterface = event.getSentryInterfaces().get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface, instanceOf(StackTraceInterface.class));

        // The object isn't exactly the same, but equals delegates to the actual exception in ImmutableThrowable.
        // This is _BAD_ and shouldn't be done, but it's the best way to do it in this particular case.
        assertThat(((StackTraceInterface) stackTraceInterface).getThrowable(), Matchers.<Object>equalTo(exception));
    }

    @Test
    public void testLogParametrisedMessage() throws Exception {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String message = UUID.randomUUID().toString();
        List<String> parameters = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        Event event;

        logAnyLevel(message, parameters);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        assertThat(event.getSentryInterfaces().get(MessageInterface.MESSAGE_INTERFACE), instanceOf(MessageInterface.class));
        MessageInterface messageInterface =
                (MessageInterface) event.getSentryInterfaces().get(MessageInterface.MESSAGE_INTERFACE);

        assertThat(messageInterface.getMessage(), is(message));
        assertThat(messageInterface.getParams(), is(parameters));
    }
}

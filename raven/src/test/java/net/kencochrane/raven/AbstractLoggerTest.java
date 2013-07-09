package net.kencochrane.raven;

import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
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

    /**
     * Returns the format of the parametrised message "Some content %s %s %s" with '%s' being the wildcard.
     *
     * @return the format of the parametrised message.
     */
    public abstract String getUnformattedMessage();

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

        Throwable capturedException = ((ExceptionInterface) exceptionInterface).getThrowable();

        assertThat(capturedException.getMessage(), is(exception.getMessage()));
        assertThat(capturedException.getStackTrace(), is(exception.getStackTrace()));
    }

    @Test
    public void testLogParametrisedMessage() throws Exception {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String message = getUnformattedMessage();
        List<String> parameters = Arrays.asList(null, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        Event event;

        logAnyLevel(message, parameters);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        assertThat(event.getSentryInterfaces().get(MessageInterface.MESSAGE_INTERFACE), instanceOf(MessageInterface.class));
        MessageInterface messageInterface =
                (MessageInterface) event.getSentryInterfaces().get(MessageInterface.MESSAGE_INTERFACE);

        assertThat(messageInterface.getMessage(), is(message));
        assertThat(messageInterface.getParams(), is(parameters));
        assertThat(event.getMessage(), is(
                "Some content " + parameters.get(0)
                        + " " + parameters.get(1)
                        + " " + parameters.get(2)));
    }
}

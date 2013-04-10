package net.kencochrane.raven.jul;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.MessageInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class SentryHandlerTest {
    @Mock
    private Raven mockRaven;
    private Logger logger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new SentryHandler(mockRaven));
    }

    @Test
    public void testSimpleMessageLogging() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event event;

        String message = UUID.randomUUID().toString();
        logger.log(Level.INFO, message);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();

        assertEquals(logger.getName(), event.getLogger());
        assertEquals(message, event.getMessage());
    }

    @Test
    public void testLogLevelConversions() {
        assertLevelConverted(Event.Level.DEBUG, Level.FINEST);
        assertLevelConverted(Event.Level.DEBUG, Level.FINER);
        assertLevelConverted(Event.Level.DEBUG, Level.FINE);
        assertLevelConverted(Event.Level.DEBUG, Level.CONFIG);
        assertLevelConverted(Event.Level.INFO, Level.INFO);
        assertLevelConverted(Event.Level.WARNING, Level.WARNING);
        assertLevelConverted(Event.Level.ERROR, Level.SEVERE);
    }

    private void assertLevelConverted(Event.Level expectedLevel, Level level){
        reset(mockRaven);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        logger.log(level, null);
        verify(mockRaven).sendEvent(eventCaptor.capture());
        assertEquals(expectedLevel, eventCaptor.getValue().getLevel());
    }

    @Test
    public void testLogException() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Exception exception = new Exception();
        Event event;

        logger.log(Level.SEVERE, "message", exception);

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        SentryInterface exceptionInterface = event.getSentryInterfaces().get(ExceptionInterface.EXCEPTION_INTERFACE);
        assertThat(exceptionInterface, instanceOf(ExceptionInterface.class));

        // The object isn't exactly the same, but equals delegates to the actual exception in ImmutableThrowable.
        // This is _BAD_ and shouldn't be done, but it's the best way to do it in this particular case.
        assertTrue(((ExceptionInterface) exceptionInterface).getThrowable().equals(exception));


        SentryInterface stackTraceInterface = event.getSentryInterfaces().get(StackTraceInterface.STACKTRACE_INTERFACE);
        assertThat(stackTraceInterface, instanceOf(StackTraceInterface.class));

        // The object isn't exactly the same, but equals delegates to the actual exception in ImmutableThrowable.
        // This is _BAD_ and shouldn't be done, but it's the best way to do it in this particular case.
        assertTrue(((StackTraceInterface) stackTraceInterface).getThrowable().equals(exception));
    }

    @Test
    public void testLogParametrisedMessage() {
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        String message = UUID.randomUUID().toString();
        List<String> parameters = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        Event event;

        logger.log(Level.INFO, message, parameters.toArray());

        verify(mockRaven).sendEvent(eventCaptor.capture());
        event = eventCaptor.getValue();
        SentryInterface exceptionInterface = event.getSentryInterfaces().get(MessageInterface.MESSAGE_INTERFACE);
        assertThat(exceptionInterface, instanceOf(MessageInterface.class));

        assertEquals(message, ((MessageInterface) exceptionInterface).getMessage());
        assertEquals(parameters, ((MessageInterface) exceptionInterface).getParams());
    }
}

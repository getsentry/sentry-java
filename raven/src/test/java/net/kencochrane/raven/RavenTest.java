package net.kencochrane.raven;

import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class RavenTest {
    private Raven raven;
    @Mock
    private Connection mockConnection;
    @Mock
    private EventBuilderHelper mockBuilderHelper;
    @Mock
    private Event mockEvent;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        raven = new Raven();
        raven.setConnection(mockConnection);
    }

    @Test
    public void testSendEvent() throws Exception {
        raven.sendEvent(mockEvent);

        verify(mockConnection).send(mockEvent);
    }

    @Test
    public void testSendMessage() throws Exception {
        String message = UUID.randomUUID().toString();
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        raven.sendMessage(message);

        verify(mockConnection).send(eventArgumentCaptor.capture());
        assertThat(eventArgumentCaptor.getValue().getLevel(), equalTo(Event.Level.INFO));
        assertThat(eventArgumentCaptor.getValue().getMessage(), equalTo(message));
    }

    @Test
    public void testSendException() throws Exception {
        String message = UUID.randomUUID().toString();
        Exception exception = new Exception(message);
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        raven.sendException(exception);

        verify(mockConnection).send(eventArgumentCaptor.capture());
        assertThat(eventArgumentCaptor.getValue().getLevel(), equalTo(Event.Level.ERROR));
        assertThat(eventArgumentCaptor.getValue().getMessage(), equalTo(message));
        assertThat(eventArgumentCaptor.getValue().getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
    }

    @Test
    public void testChangeConnection() throws Exception {
        Connection mockNewConnection = mock(Connection.class);

        raven.setConnection(mockNewConnection);
        raven.sendEvent(mockEvent);

        verify(mockConnection, never()).send(mockEvent);
        verify(mockNewConnection).send(mockEvent);
    }

    @Test
    public void testAddRemoveBuilderHelpers() throws Exception {
        assertThat(raven.getBuilderHelpers(), not(contains(mockBuilderHelper)));

        raven.addBuilderHelper(mockBuilderHelper);
        assertThat(raven.getBuilderHelpers(), contains(mockBuilderHelper));
        raven.removeBuilderHelper(mockBuilderHelper);
        assertThat(raven.getBuilderHelpers(), not(contains(mockBuilderHelper)));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly() throws Exception {
        raven.getBuilderHelpers().add(mockBuilderHelper);
    }

    @Test
    public void testRunBuilderHelpers() throws Exception {
        EventBuilder eventBuilder = mock(EventBuilder.class);
        raven.addBuilderHelper(mockBuilderHelper);

        raven.runBuilderHelpers(eventBuilder);

        verify(mockBuilderHelper).helpBuildingEvent(eventBuilder);
    }
}

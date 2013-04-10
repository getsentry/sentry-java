package net.kencochrane.raven;

import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.Event;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class RavenTest {
    @Mock
    private Connection mockConnection;
    private Raven raven;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        raven = new Raven(mockConnection);
    }

    @Test
    public void testSendEvent() {
        Event event = mock(Event.class);
        raven.sendEvent(event);

        verify(mockConnection).send(event);
    }
}

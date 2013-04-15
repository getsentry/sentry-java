package net.kencochrane.raven;

import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RavenTest {
    @Mock
    private Connection mockConnection;
    @Mock
    private EventBuilderHelper mockBuilderHelper;
    @Mock
    private Event mockEvent;
    private Raven raven;

    @Before
    public void setUp() throws Exception {
        raven = new Raven(mockConnection);
    }

    @Test
    public void testSendEvent() throws Exception {
        raven.sendEvent(mockEvent);

        verify(mockConnection).send(mockEvent);
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

    @Test(expected = UnsupportedOperationException.class)
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

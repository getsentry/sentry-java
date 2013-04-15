package net.kencochrane.raven;

import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;

public class RavenTest {
    @Mock
    private Connection mockConnection;
    private Raven raven;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        raven = new Raven(mockConnection);
    }

    @Test
    public void testSendEvent() throws Exception {
        Event event = mock(Event.class);
        raven.sendEvent(event);

        verify(mockConnection).send(event);
    }

    @Test
    public void testChangeConnection() throws Exception {
        Event event = mock(Event.class);
        Connection mockNewConnection = mock(Connection.class);

        raven.setConnection(mockNewConnection);
        raven.sendEvent(event);

        verify(mockConnection, never()).send(event);
        verify(mockNewConnection).send(event);
    }

    @Test
    public void testAddRemoveBuilderHelpers() throws Exception {
        EventBuilderHelper builderHelper = mock(EventBuilderHelper.class);
        assertThat(raven.getBuilderHelpers(), not(contains(builderHelper)));

        raven.addBuilderHelper(builderHelper);
        assertThat(raven.getBuilderHelpers(), contains(builderHelper));
        raven.removeBuilderHelper(builderHelper);
        assertThat(raven.getBuilderHelpers(), not(contains(builderHelper)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly() throws Exception {
        EventBuilderHelper builderHelper = mock(EventBuilderHelper.class);
        raven.getBuilderHelpers().add(builderHelper);
    }

    @Test
    public void testRunBuilderHelpers() throws Exception {
        EventBuilder eventBuilder = mock(EventBuilder.class);
        EventBuilderHelper builderHelper = mock(EventBuilderHelper.class);
        raven.addBuilderHelper(builderHelper);

        raven.runBuilderHelpers(eventBuilder);

        verify(builderHelper).helpBuildingEvent(eventBuilder);
    }
}

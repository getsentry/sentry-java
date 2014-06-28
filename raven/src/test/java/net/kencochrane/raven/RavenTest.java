package net.kencochrane.raven;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.Verifications;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static mockit.Deencapsulation.getField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class RavenTest {
    @Tested
    private Raven raven = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private Event mockEvent = null;

    @AfterMethod
    public void tearDown() throws Exception {
        ThreadLocal<AtomicInteger> ravenThread = getField(Raven.class, "RAVEN_THREAD");
        ravenThread.remove();
    }

    @Test
    public void testSendEvent() throws Exception {
        raven.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendEventFailingIsCaught() throws Exception {
        new NonStrictExpectations(){{
            mockConnection.send((Event) any);
            result = new RuntimeException();
        }};
        raven.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }

    @Test
    public void testSendMessage() throws Exception {
        final String message = "e960981e-656d-4404-9b1d-43b483d3f32c";

        raven.sendMessage(message);

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.INFO));
            assertThat(event.getMessage(), equalTo(message));
        }};
    }

    @Test
    public void testSendException() throws Exception {
        final String message = "7b61ddb1-eb32-428d-bad9-a7d842605ba7";
        final Exception exception = new Exception(message);

        raven.sendException(exception);

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getLevel(), equalTo(Event.Level.ERROR));
            assertThat(event.getMessage(), equalTo(message));
            assertThat(event.getSentryInterfaces(), hasKey(ExceptionInterface.EXCEPTION_INTERFACE));
        }};
    }

    @Test
    public void testChangeConnection(@Injectable final Connection mockNewConnection) throws Exception {
        raven.setConnection(mockNewConnection);

        raven.sendEvent(mockEvent);

        new Verifications() {{
            mockConnection.send((Event) any);
            times = 0;
            mockNewConnection.send(mockEvent);
        }};
    }

    @Test
    public void testAddRemoveBuilderHelpers(@Injectable final EventBuilderHelper mockBuilderHelper) throws Exception {
        assertThat(raven.getBuilderHelpers(), not(contains(mockBuilderHelper)));

        raven.addBuilderHelper(mockBuilderHelper);
        assertThat(raven.getBuilderHelpers(), contains(mockBuilderHelper));
        raven.removeBuilderHelper(mockBuilderHelper);
        assertThat(raven.getBuilderHelpers(), not(contains(mockBuilderHelper)));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCantModifyBuilderHelpersDirectly(@Injectable final EventBuilderHelper mockBuilderHelper) throws Exception {
        raven.getBuilderHelpers().add(mockBuilderHelper);
    }

    @Test
    public void testRunBuilderHelpers(@Injectable final EventBuilderHelper mockBuilderHelper,
                                      @Injectable final EventBuilder mockEventBuilder) throws Exception {
        raven.addBuilderHelper(mockBuilderHelper);

        raven.runBuilderHelpers(mockEventBuilder);

        new Verifications() {{
            mockBuilderHelper.helpBuildingEvent(mockEventBuilder);
        }};
    }

    @Test
    public void testThreadNotManagedByDefault() throws Exception {
        assertThat(Raven.isManagingThread(), is(false));
    }

    @Test
    public void testStartManagingThreadWorks() throws Exception {
        Raven.startManagingThread();

        assertThat(Raven.isManagingThread(), is(true));
    }

    @Test
    public void testStartManagingAlreadyManagedThreadWorks() throws Exception {
        Raven.startManagingThread();

        Raven.startManagingThread();

        assertThat(Raven.isManagingThread(), is(true));
    }

    @Test
    public void testStopManagingThreadWorks() throws Exception {
        Raven.startManagingThread();

        Raven.stopManagingThread();

        assertThat(Raven.isManagingThread(), is(false));
    }

    @Test
    public void testStopManagingNonManagedThreadWorks() throws Exception {
        Raven.stopManagingThread();

        assertThat(Raven.isManagingThread(), is(false));
    }

    @Test
    public void testThreadManagedTwiceNeedsToBeUnmanagedTwice() throws Exception {
        Raven.startManagingThread();
        Raven.startManagingThread();

        Raven.stopManagingThread();
        assertThat(Raven.isManagingThread(), is(true));
        Raven.stopManagingThread();
        assertThat(Raven.isManagingThread(), is(false));
    }
}

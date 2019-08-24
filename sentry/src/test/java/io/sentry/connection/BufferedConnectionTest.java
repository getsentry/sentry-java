package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.buffer.Buffer;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.time.FixedClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.NotSerializableException;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class BufferedConnectionTest extends BaseTest {
    private static final Date FIXED_DATE = new Date(1483228800L);
    private FixedClock fixedClock = new FixedClock(FIXED_DATE);
    private LockdownManager lockdownManager = new LockdownManager(fixedClock);

    private Set<Event> bufferedEvents;
    private List<Event> sentEvents;
    private AbstractConnection mockConnection;
    private Connection bufferedConnection;

    @Before
    public void setup() throws Exception {
        bufferedEvents = new HashSet<>();
        sentEvents = new ArrayList<>();
        fixedClock = new FixedClock(FIXED_DATE);
        lockdownManager = new LockdownManager(fixedClock);

        mockConnection = mock(AbstractConnection.class, withSettings()
            .useConstructor("public", "private", lockdownManager)
            .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        collectSentEvents();
        doNothing().when(mockConnection).addEventSendCallback(any(EventSendCallback.class));

        Buffer mockBuffer = new Buffer() {
            @Override
            public void add(Event event) {
                synchronized (bufferedEvents) {
                    bufferedEvents.add(event);
                }
            }

            @Override
            public void discard(Event event) {
                synchronized (bufferedEvents) {
                    bufferedEvents.remove(event);
                }
            }

            @Override
            public Iterator<Event> getEvents() {
                synchronized (bufferedEvents) {
                    return new ArrayList<>(bufferedEvents).iterator();
                }
            }
        };

        int flushtime = 10;
        int shutdownTimeout = 0;
        BufferedConnection innerBufferedConnection = new BufferedConnection(mockConnection, mockBuffer, flushtime, false, shutdownTimeout);
        this.bufferedConnection = innerBufferedConnection.wrapConnectionWithBufferWriter(innerBufferedConnection);
    }

    private void collectSentEvents() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                sentEvents.add(invocation.getArgument(0, Event.class));
                return null;
            }
        }).when(mockConnection).doSend(any(Event.class));
    }

    @After
    public void teardown() throws IOException {
        bufferedConnection.close();
    }

    @Test
    public void test() throws Exception {
        Event event = new EventBuilder().build();

        doThrow(new ConnectionException()).when(mockConnection).doSend(any(Event.class));

        try {
            bufferedConnection.send(event);
            fail();
        } catch (ConnectionException e) {

        }

        assertThat(bufferedEvents.size(), equalTo(1));
        assertThat(bufferedEvents.iterator().next(), equalTo(event));

        // Attempt sending a second event (should be in lockdown)
        Event event2 = new EventBuilder().build();
        try {
            bufferedConnection.send(event2);
            fail();
        } catch (LockedDownException e) {

        }
        assertThat(bufferedEvents.size(), equalTo(2));

        // End the lockdown
        fixedClock.tick(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME, TimeUnit.MILLISECONDS);

        collectSentEvents();

        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                synchronized (bufferedEvents) {
                    return bufferedEvents.size() == 0;
                }
            }
        });

        assertThat(bufferedEvents.size(), equalTo(0));
        assertThat(sentEvents.contains(event), is(true));
        assertThat(sentEvents.contains(event2), is(true));
    }

    @Test
    public void testNotSerializableNotBuffered() throws Exception {
        Event event = new EventBuilder().build();
        doThrow(new ConnectionException("NonSerializable", new NotSerializableException()))
                .when(mockConnection).send(any(Event.class));

        try {
            bufferedConnection.send(event);
        } catch (ConnectionException e) {

        }
        assertThat(bufferedEvents.size(), equalTo(0));
    }

    @Test
    public void test500NotBuffered() throws Exception {
        Event event = new EventBuilder().build();
        doThrow(new ConnectionException("500", new IOException(), null, HttpURLConnection.HTTP_INTERNAL_ERROR))
                .when(mockConnection).send(any(Event.class));
        try {
            bufferedConnection.send(event);
        } catch (ConnectionException e) {

        }
        assertThat(bufferedEvents.size(), equalTo(0));
    }

    @Test
    public void test429IsNotBuffered() throws Exception {
        Event event = new EventBuilder().build();
        doThrow(new ConnectionException("429", new IOException(), null, HttpConnection.HTTP_TOO_MANY_REQUESTS))
                .when(mockConnection).send(any(Event.class));
        try {
            bufferedConnection.send(event);
        } catch (Exception e) {

        }
        assertThat(bufferedEvents.size(), equalTo(0));
    }

    @Test
    public void testNoResponseCodeIsBuffered() throws Exception {
        Event event = new EventBuilder().build();
        doThrow(new ConnectionException("NoResponseCode", new IOException(), null, null))
                .when(mockConnection).send(any(Event.class));
        try {
            bufferedConnection.send(event);
        } catch (Exception e) {

        }
        assertThat(bufferedEvents.size(), equalTo(1));
    }
}

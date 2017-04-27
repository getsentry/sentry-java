package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.buffer.Buffer;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.time.FixedClock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.testng.collections.Sets;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;

public class BufferedConnectionTest extends BaseTest {
    private static final Date FIXED_DATE = new Date(1483228800L);
    private FixedClock fixedClock = new FixedClock(FIXED_DATE);
    private LockdownManager lockdownManager = new LockdownManager(fixedClock);

    private Set<Event> bufferedEvents;
    private List<Event> sentEvents;
    private Buffer mockBuffer;
    private Connection mockConnection;
    private Connection bufferedConnection;
    private volatile boolean connectionUp;

    @BeforeMethod
    public void setup() {
        bufferedEvents = Sets.newHashSet();
        sentEvents = Lists.newArrayList();
        connectionUp = true;

        mockConnection = new AbstractConnection("public", "private") {
            @Override
            protected void doSend(Event event) throws ConnectionException {
                if (connectionUp) {
                    sentEvents.add(event);
                } else {
                    throw new ConnectionException("Connection is down.");
                }
            }

            @Override
            public void addEventSendCallback(EventSendCallback eventSendCallback) {

            }

            @Override
            public void close() throws IOException {

            }
        };

        fixedClock = new FixedClock(FIXED_DATE);
        lockdownManager = new LockdownManager(fixedClock);

        mockBuffer = new Buffer() {
            @Override
            public void add(Event event) {
                bufferedEvents.add(event);
            }

            @Override
            public void discard(Event event) {
                bufferedEvents.remove(event);
            }

            @Override
            public Iterator<Event> getEvents() {
                return Lists.newArrayList(bufferedEvents).iterator();
            }
        };

        int flushtime = 10;
        int shutdownTimeout = 0;
        BufferedConnection innerBufferedConnection = new BufferedConnection(mockConnection, mockBuffer, flushtime, false, shutdownTimeout);
        this.bufferedConnection = innerBufferedConnection.wrapConnectionWithBufferWriter(innerBufferedConnection);
    }

    @AfterMethod
    public void teardown() throws IOException {
        bufferedConnection.close();
    }

    @Test
    public void test() throws Exception {
        setField(mockConnection, "lockdownManager", lockdownManager);

        Event event = new EventBuilder().build();
        connectionUp = false;
        try {
            bufferedConnection.send(event);
        } catch (Exception e) {

        }
        assertThat(bufferedEvents.size(), equalTo(1));
        assertThat(bufferedEvents.iterator().next(), equalTo(event));

        // Attempt sending a second event (should be in lockdown)
        Event event2 = new EventBuilder().build();
        try {
            bufferedConnection.send(event2);
        } catch (Exception e) {

        }
        assertThat(bufferedEvents.size(), equalTo(2));

        // End the lockdown
        fixedClock.tick(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME, TimeUnit.MILLISECONDS);

        connectionUp = true;
        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return bufferedEvents.size() == 0;
            }
        });
        assertThat(bufferedEvents.size(), equalTo(0));
        assertThat(sentEvents.contains(event), is(true));
        assertThat(sentEvents.contains(event2), is(true));
    }
}

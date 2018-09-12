package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.environment.Version;
import io.sentry.time.FixedClock;
import mockit.*;
import io.sentry.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.AssertJUnit.fail;

public class AbstractConnectionTest extends BaseTest {
    private static final Date FIXED_DATE = new Date(1483228800L);
    @Injectable
    private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
    @Injectable
    private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";
    @Tested
    private AbstractConnection abstractConnection = null;
    private FixedClock fixedClock = new FixedClock(FIXED_DATE);
    private LockdownManager lockdownManager = new LockdownManager(fixedClock);

    @BeforeMethod
    public void setup() {
        fixedClock = new FixedClock(FIXED_DATE);
        lockdownManager = new LockdownManager(fixedClock);
    }

    @Test
    public void testAuthHeader() throws Exception {
        String authHeader = abstractConnection.getAuthHeader();

        assertThat(authHeader, is("Sentry sentry_version=6,"
                + "sentry_client=sentry-java/" + Version.SDK_VERSION + ","
                + "sentry_key=" + publicKey + ","
                + "sentry_secret=" + secretKey));
    }

    @Test
    public void testSuccessfulSendCallsDoSend(@Injectable final Event mockEvent) throws Exception {
        abstractConnection.send(mockEvent);

        new Verifications() {{
            abstractConnection.doSend(mockEvent);
        }};
    }

    @Test
    public void testExceptionOnSendStartLockDown(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lockdownManager", lockdownManager);

        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        Date lockdownStartTime = getField(lockdownManager, "lockdownStartTime");
        assertThat(lockdownStartTime, is(FIXED_DATE));

        // Send while in lockdown throws LockedDownException
        try {
            abstractConnection.send(mockEvent);
            fail();
        } catch (LockedDownException e) {
            // ignore
        }
    }

    @Test
    public void testLockDownDoublesTheTime(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lockdownManager", lockdownManager);

        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        // Check for default lockdown time
        long lockdownTimeAfter = getField(lockdownManager, "lockdownTime");
        assertThat(lockdownTimeAfter, is(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME));

        // Roll forward by the base lockdown time, allowing the lockdown to retried
        fixedClock.tick(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME, TimeUnit.MILLISECONDS);

        // Send a second event, doubling the lockdown
        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        // Check for doubled lockdown time
        long lockdownTimeAfter2 = getField(lockdownManager, "lockdownTime");
        assertThat(lockdownTimeAfter2, is(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME * 2));
    }

    @Test
    public void testLockDownDoesntDoubleItAtMax(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lockdownManager", lockdownManager);
        setField(lockdownManager, "lockdownTime", LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME);
        setField(lockdownManager, "lockdownStartTime", fixedClock.date());

        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long lockdownTimeAfter = getField(lockdownManager, "lockdownTime");
        assertThat(lockdownTimeAfter, is(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME));
    }

    @Test
    public void testEventSendCallbackSuccess(@Injectable final Event mockEvent) throws Exception {
        final AtomicBoolean callbackCalled = new AtomicBoolean(false);
        EventSendCallback callback = new EventSendCallback() {

            @Override
            public void onFailure(Event event, Exception exception) {

            }

            @Override
            public void onSuccess(Event event) {
                callbackCalled.set(true);
            }

        };
        HashSet<EventSendCallback> callbacks = new HashSet<>();
        callbacks.add(callback);

        setField(abstractConnection, "eventSendCallbacks", callbacks);
        abstractConnection.send(mockEvent);

        assertThat(callbackCalled.get(), is(true));
    }

    @Test
    public void testEventSendCallbackFailure(@Injectable final Event mockEvent) throws Exception {
        final AtomicBoolean callbackCalled = new AtomicBoolean(false);
        EventSendCallback callback = new EventSendCallback() {

            @Override
            public void onFailure(Event event, Exception exception) {
                callbackCalled.set(true);
            }

            @Override
            public void onSuccess(Event event) {

            }

        };
        HashSet<EventSendCallback> callbacks = new HashSet<>();
        callbacks.add(callback);

        setField(abstractConnection, "eventSendCallbacks", callbacks);
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        assertThat(callbackCalled.get(), is(true));
    }

    @Test
    public void testRecommendedLockdownRespected(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lockdownManager", lockdownManager);

        final long recommendedLockdownWaitTime = 12345L;
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException("Message", null, recommendedLockdownWaitTime, HttpConnection.HTTP_TOO_MANY_REQUESTS);
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long lockdownTimeAfter = getField(lockdownManager, "lockdownTime");
        assertThat(lockdownTimeAfter, is(recommendedLockdownWaitTime));
    }
}

package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.environment.Version;
import io.sentry.time.FixedClock;
import io.sentry.event.Event;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class AbstractConnectionTest extends BaseTest {
    private static final Date FIXED_DATE = new Date(1483228800L);
    private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
    private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";

    private AbstractConnection abstractConnection = null;
    private FixedClock fixedClock;
    private LockdownManager lockdownManager;

    @Before
    public void setup() {
        fixedClock = new FixedClock(FIXED_DATE);

        lockdownManager = mock(LockdownManager.class, withSettings()
                .useConstructor(fixedClock)
                .defaultAnswer(CALLS_REAL_METHODS));

        abstractConnection = mock(AbstractConnection.class, withSettings()
                .useConstructor(publicKey, secretKey, lockdownManager)
                .defaultAnswer(CALLS_REAL_METHODS));
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
    public void testSuccessfulSendCallsDoSend() throws Exception {
        final Event mockEvent = mock(Event.class);

        abstractConnection.send(mockEvent);

        verify(abstractConnection).doSend(mockEvent);
    }

    @Test
    public void testExceptionOnSendStartLockDown() throws Exception {
        final Event mockEvent = mock(Event.class);

        doThrow(new ConnectionException()).when(abstractConnection).doSend(any(Event.class));

        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        verify(lockdownManager).lockdown(any(ConnectionException.class));
        assertTrue(lockdownManager.isLockedDown());

        // Send while in lockdown throws LockedDownException
        try {
            abstractConnection.send(mockEvent);
            fail();
        } catch (LockedDownException e) {
            // ignore
        }
    }

    @Test
    public void testLockDownDoublesTheTime() throws Exception {
        Event mockEvent = mock(Event.class);

        doThrow(new ConnectionException()).when(abstractConnection).doSend(any(Event.class));

        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        // Check for default lockdown time
        long lockdownTimeAfter = lockdownManager.getLockdownTime();
        assertThat(lockdownTimeAfter, is(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME));

        // Roll forward by the base lockdown time, allowing the lockdown to retried
        fixedClock.tick(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME, TimeUnit.MILLISECONDS);

        // Send a second event, doubling the lockdown
        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        // Check for doubled lockdown time
        long lockdownTimeAfter2 = lockdownManager.getLockdownTime();
        assertThat(lockdownTimeAfter2, is(LockdownManager.DEFAULT_BASE_LOCKDOWN_TIME * 2));
    }

    @Test
    public void testLockDownDoesntDoubleItAtMax() throws Exception {
        Event mockEvent = mock(Event.class);

        lockdownManager.setBaseLockdownTime(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME);

        doThrow(new ConnectionException()).when(abstractConnection).doSend(any(Event.class));

        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        long lockdownTimeAfter = lockdownManager.getLockdownTime();
        assertThat(lockdownTimeAfter, is(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME));

        try {
            abstractConnection.send(mockEvent);
        } catch (LockedDownException e) {
            // ignore
        }

        lockdownTimeAfter = lockdownManager.getLockdownTime();
        assertThat(lockdownTimeAfter, is(LockdownManager.DEFAULT_MAX_LOCKDOWN_TIME));
    }

    @Test
    public void testEventSendCallbackSuccess() throws Exception {
        Event mockEvent = mock(Event.class);

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

        abstractConnection.addEventSendCallback(callback);

        abstractConnection.send(mockEvent);

        assertThat(callbackCalled.get(), is(true));
    }

    @Test
    public void testEventSendCallbackFailure() throws Exception {
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

        abstractConnection.addEventSendCallback(callback);

        doThrow(new ConnectionException()).when(abstractConnection).doSend(any(Event.class));

        Event mockEvent = mock(Event.class);

        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        assertThat(callbackCalled.get(), is(true));
    }

    @Test
    public void testRecommendedLockdownRespected() throws Exception {
        Event mockEvent = mock(Event.class);

        final long recommendedLockdownWaitTime = 12345L;

        doThrow(new ConnectionException("Message", null, recommendedLockdownWaitTime,
                HttpConnection.HTTP_TOO_MANY_REQUESTS))
                .when(abstractConnection).doSend(any(Event.class));

        try {
            abstractConnection.send(mockEvent);
        } catch (ConnectionException e) {
            // ignore
        }

        long lockdownTimeAfter = lockdownManager.getLockdownTime();
        assertThat(lockdownTimeAfter, is(recommendedLockdownWaitTime));
    }
}

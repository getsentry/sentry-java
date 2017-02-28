package com.getsentry.raven.connection;

import com.getsentry.raven.time.Clock;
import com.getsentry.raven.time.FixedClock;
import mockit.*;
import com.getsentry.raven.event.Event;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AbstractConnectionTest {
    private static final Date FIXED_DATE = new Date(1483228800L);
    @Injectable
    private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
    @Injectable
    private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";
    @Tested
    private AbstractConnection abstractConnection = null;
    @Injectable
    private final Clock mockClock = new FixedClock(FIXED_DATE);

    @Test
    public void testAuthHeader() throws Exception {
        String authHeader = abstractConnection.getAuthHeader();

        assertThat(authHeader, is("Sentry sentry_version=6,"
                + "sentry_client=raven-java/test,"
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
        setField(abstractConnection, "clock", mockClock);

        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        Date lockdownStartTime = getField(abstractConnection, "lockdownStartTime");
        assertThat(lockdownStartTime, is(FIXED_DATE));
    }

    @Test
    public void testLockDownDoublesTheWaitingTime(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "clock", mockClock);

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
        long lockdownWaitingTimeAfter = getField(abstractConnection, "lockdownWaitingTime");
        assertThat(lockdownWaitingTimeAfter, is(AbstractConnection.DEFAULT_BASE_WAITING_TIME));

        // Roll forward 10ms, allowing the lockdown to retried
        ((FixedClock) mockClock).tick(10, TimeUnit.MILLISECONDS);

        // Send a second event, doubling the lockdown
        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        // Check for doubled lockdown time
        long lockdownWaitingTimeAfter2 = getField(abstractConnection, "lockdownWaitingTime");
        assertThat(lockdownWaitingTimeAfter2, is(AbstractConnection.DEFAULT_BASE_WAITING_TIME * 2));
    }

    @Test
    public void testLockDownDoesntDoubleItAtMax(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lockdownWaitingTime", AbstractConnection.DEFAULT_MAX_WAITING_TIME);
        setField(abstractConnection, "lockdownStartTime", mockClock.date());
        setField(abstractConnection, "clock", mockClock);

        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long lockdownWaitingTimeAfter = getField(abstractConnection, "lockdownWaitingTime");
        assertThat(lockdownWaitingTimeAfter, is(AbstractConnection.DEFAULT_MAX_WAITING_TIME));
    }

    @Test
    public void testEventSendFailureCallback(@Injectable final Event mockEvent) throws Exception {
        final AtomicBoolean callbackCalled = new AtomicBoolean(false);
        EventSendFailureCallback callback = new EventSendFailureCallback() {

            @Override
            public void onFailure(Event event, Exception exception) {
                callbackCalled.set(true);
            }

        };
        HashSet<EventSendFailureCallback> callbacks = new HashSet<>();
        callbacks.add(callback);

        setField(abstractConnection, "eventSendFailureCallbacks", callbacks);
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
        setField(abstractConnection, "clock", mockClock);

        final long recommendedLockdownWaitTime = 12345L;
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException("Message", null, recommendedLockdownWaitTime);
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long lockdownWaitingTimeAfter = getField(abstractConnection, "lockdownWaitingTime");
        assertThat(lockdownWaitingTimeAfter, is(recommendedLockdownWaitTime));
    }
}

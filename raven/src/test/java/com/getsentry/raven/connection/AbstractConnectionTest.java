package com.getsentry.raven.connection;

import mockit.*;
import com.getsentry.raven.event.Event;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AbstractConnectionTest {
    @Injectable
    private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
    @Injectable
    private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";
    @Tested
    private AbstractConnection abstractConnection = null;
    @Injectable
    private Lock mockLock = null;
    @Injectable
    private Condition mockCondition = null;
    //Disable thread sleep during the tests
    @Mocked("sleep")
    private Thread mockThread = null;

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
        setField(abstractConnection, "lock", mockLock);
        setField(abstractConnection, "condition", mockCondition);

        abstractConnection.send(mockEvent);

        new Verifications() {{
            abstractConnection.doSend(mockEvent);
        }};
    }

    @Test
    public void testExceptionOnSendStartLockDown(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lock", mockLock);
        setField(abstractConnection, "condition", mockCondition);
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        new Verifications() {{
            mockLock.lock();
            mockCondition.signalAll();
            mockLock.unlock();
        }};
    }

    @Test
    public void testLockDownDoublesTheWaitingTime(@Injectable final Event mockEvent) throws Exception {
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long waitingTimeAfter = getField(abstractConnection, "waitingTime");
        assertThat(waitingTimeAfter, is(AbstractConnection.DEFAULT_BASE_WAITING_TIME * 2));
        new Verifications() {{
            Thread.sleep(AbstractConnection.DEFAULT_BASE_WAITING_TIME);
        }};
    }

    @Test
    public void testLockDownDoesntDoubleItAtMax(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "waitingTime", AbstractConnection.DEFAULT_MAX_WAITING_TIME);
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        try {
            abstractConnection.send(mockEvent);
        } catch (Exception e) {
            // ignore
        }

        long waitingTimeAfter = getField(abstractConnection, "waitingTime");
        assertThat(waitingTimeAfter, is(AbstractConnection.DEFAULT_MAX_WAITING_TIME));
        new Verifications() {{
            Thread.sleep(AbstractConnection.DEFAULT_MAX_WAITING_TIME);
        }};
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

}

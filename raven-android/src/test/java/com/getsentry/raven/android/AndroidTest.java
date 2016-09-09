package com.getsentry.raven.android;

import com.getsentry.raven.stub.SentryStub;
import org.junit.After;
import org.junit.Before;

import java.util.concurrent.Callable;

public class AndroidTest {

    protected SentryStub sentryStub;

    @Before
    public void setUp() throws Exception {
        sentryStub = new SentryStub();
        sentryStub.removeEvents();
    }

    @After
    public void tearDown() throws Exception {
        Raven.clearStoredRaven();
        sentryStub.removeEvents();
    }

    /**
     * To avoid littering tests with static Thread.sleep calls (because Android code must do async I/O),
     * we use this method to repeatedly test a predicate with a maximum wait time, returning as
     * soon as we see that it's true so that tests can move along promptly.
     *
     * @param maxWaitMs maximum time to wait in milliseconds
     * @param predicate Callable that returns a boolean
     * @throws Exception thrown is maximum time is used up, or Callable throws its own exception
     */
    public void waitUntilTrue(int maxWaitMs, Callable<Boolean> predicate) throws Exception {
        long until = System.currentTimeMillis() + maxWaitMs;
        // pause one one-hundredths of the max delay between predicate checks
        int pauseMs = maxWaitMs / 100;
        while (System.currentTimeMillis() < until) {
            boolean response = predicate.call();
            if (response) {
                return;
            }
            Thread.sleep(pauseMs);
        }
        throw new RuntimeException("Waited too long for predicate to come true.");
    }

}
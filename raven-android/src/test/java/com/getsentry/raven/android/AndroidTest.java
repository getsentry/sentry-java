package com.getsentry.raven.android;

import com.getsentry.raven.BaseTest;
import com.getsentry.raven.stub.SentryStub;
import org.junit.After;
import org.junit.Before;

public class AndroidTest extends BaseTest {

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

}
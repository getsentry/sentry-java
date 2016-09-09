package com.getsentry.raven.android;

import com.getsentry.raven.stub.SentryStub;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RavenIT {
    private SentryStub sentryStub;

    @Before
    public void setUp() throws Exception {
        sentryStub = new SentryStub();
        sentryStub.removeEvents();
    }

    @After
    public void tearDown() throws Exception {
        sentryStub.removeEvents();
    }

    @Test
    public void test() {
        Assert.assertEquals(sentryStub.getEventCount(), 0);
        TestActivity activity = Robolectric.setupActivity(TestActivity.class);
        activity.sendEvent();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertEquals(sentryStub.getEventCount(), 1);
    }
}

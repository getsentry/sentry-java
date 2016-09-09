package com.getsentry.raven.android;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.stub.SentryStub;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class EventCacheIT extends AndroidTest {

    @Test
    public void test() throws Exception {
        Assert.assertEquals(sentryStub.getEventCount(), 0);

        EventCacheITActivity activity = Robolectric.setupActivity(EventCacheITActivity.class);
        Event event = new EventBuilder().withMessage("message").build();
        activity.setupEventCache(new File("./cache"), 2);
        activity.storeEvent(event);
        activity.flushEvents();

        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return sentryStub.getEventCount() == 1;
            }
        });

        Assert.assertEquals(sentryStub.getEventCount(), 1);
    }

}

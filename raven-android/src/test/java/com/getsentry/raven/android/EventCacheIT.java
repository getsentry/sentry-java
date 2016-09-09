package com.getsentry.raven.android;

import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import org.apache.maven.artifact.ant.shaded.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class EventCacheIT extends AndroidTest {

    private static final File CACHE_DIR = new File("./test-cache-directory");

    @After
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(CACHE_DIR);
    }

    @Test
    public void test() throws Exception {
        Assert.assertEquals(sentryStub.getEventCount(), 0);

        EventCacheITActivity activity = Robolectric.setupActivity(EventCacheITActivity.class);
        Event event = new EventBuilder().withMessage("message").build();
        activity.setupEventCache(CACHE_DIR, 2);
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

package io.sentry.android;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class SentryAndroidIT extends AndroidTest {

    @Test
    public void test() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryITActivity activity = Robolectric.setupActivity(SentryITActivity.class);
        Assert.assertEquals(activity.getCustomFactoryUsed(), true);

        activity.sendEvent();
        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return getStoredEvents().size() == 1;
            }
        });

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

}

package io.sentry.android;

import io.sentry.BaseIT;
import io.sentry.Sentry;
import org.junit.After;
import org.junit.Before;

public class AndroidTest extends BaseIT {

    @Before
    public void setup() {
        stub200ForProject1Store();
    }

    @After
    public void tearDown() throws Exception {
        Sentry.setStoredClient(null);
    }

}

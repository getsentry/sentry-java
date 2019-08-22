package io.sentry;

import org.junit.Before;

public class BaseJUnitTest {

    @Before
    public void resetClient() {
        Sentry.setStoredClient(null);
    }
}

package io.sentry;

import io.sentry.connection.AbstractConnection;
import io.sentry.connection.LockdownManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static mockit.Deencapsulation.getField;

public class SentryIT extends BaseIT {

    @Test
    public void test500DoesLockdown() throws Exception {
        stubForProject1Store(500);

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryClient client = SentryClientFactory.sentryClient();
        client.sendMessage("Test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(0);

        assertTrue(isLockedDown(client));
    }

    @Test
    public void test403FilteredDoesNotLockdown() throws Exception {
        stubForProject1Store(403);

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryClient client = SentryClientFactory.sentryClient();
        client.sendMessage("Test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(0);

        assertFalse(isLockedDown(client));
    }

    @Test
    public void testSuccess() throws Exception {
        stub200ForProject1Store();

        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryClient client = SentryClientFactory.sentryClient();
        client.sendMessage("Test");

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);

        assertFalse(isLockedDown(client));
    }

    private boolean isLockedDown(SentryClient client) {
        AbstractConnection connection = getField(client, "connection");
        LockdownManager lockdownManager = getField(connection, "lockdownManager");
        return lockdownManager.isLockedDown();
    }

}

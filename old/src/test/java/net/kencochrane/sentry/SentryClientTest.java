package net.kencochrane.sentry;

import mockit.Mocked;
import mockit.Expectations;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SentryClientTest {
    public void triggerRuntimeException() {
        try {
            triggerNullPointer();
        } catch (Exception e) {
            throw new RuntimeException("Error triggering null pointer", e);
        }
    }

    public String triggerNullPointer() {
        String c = null;
        return c.toLowerCase();
    }

    @Test(expected=RuntimeException.class)
    public void testMissingDSN() {
        RavenClient client = new RavenClient();
    }

    @Test
    public void testConfigureFromDSN() {
        RavenClient client = new RavenClient("http://public:secret@example.com/path/1");
        assertEquals(client.getSentryDSN(), "http://public:secret@example.com/path/1");
    }

    @Test
    public void testConfigureFromEnvironment() {
        new Expectations()
        {
            @Mocked("getenv") System mockedSystem;

            {
                System.getenv("SENTRY_DSN"); returns("http://public:secret@example.com/path/1");
            }
        };
        RavenClient client = new RavenClient();
        assertEquals(client.getSentryDSN(), "http://public:secret@example.com/path/1");
   }

    @Test
    public void testCaptureExceptionWithOnlyThrowable() {
        RavenClient client = new RavenClient("http://public:secret@example.com/path/1");

        new Expectations()
        {

            @Mocked("getRandomUUID") RavenUtils mockedRavenUtils;

            {
                RavenUtils.getRandomUUID(); returns("1234567890");
            }

        // TODO: this should be mocked, somehow
        //     RavenClient.sendMessage(); minTimes = 1; maxTimes = 1;
        };


        try {
            triggerRuntimeException();
        } catch (RuntimeException e) {
            String ident = client.captureException(e);
            assertEquals(ident, "1234567890");
        }
    }
}
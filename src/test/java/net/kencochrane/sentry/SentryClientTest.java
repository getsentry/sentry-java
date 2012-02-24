package net.kencochrane.sentry;

import mockit.Mocked;
import mockit.Expectations;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SentryClientTest {
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
}
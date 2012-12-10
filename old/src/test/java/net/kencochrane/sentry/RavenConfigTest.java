package net.kencochrane.sentry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RavenConfigTest
{
    @Test
    public void testDSNComponents() {

        // Without Port
        RavenClient client = new RavenClient("http://public:secret@example.com/path/sentry/1");
        assertEquals("http://public:secret@example.com/path/sentry/1", client.getSentryDSN());
        assertEquals("example.com", client.getConfig().getHost());
        assertEquals("/path/sentry", client.getConfig().getPath());
        assertEquals(-1, client.getConfig().getPort());
        assertEquals("1", client.getConfig().getProjectId());
        assertEquals("http://example.com/path/sentry/api/store/", client.getConfig().getSentryURL());

        // With Port
        client = new RavenClient("http://public:secret@example.com:9000/path/sentry/1");
        assertEquals(9000, client.getConfig().getPort());
        assertEquals("http://example.com:9000/path/sentry/api/store/", client.getConfig().getSentryURL());
    }
}

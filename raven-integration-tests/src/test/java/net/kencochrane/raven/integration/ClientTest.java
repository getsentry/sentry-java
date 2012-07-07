package net.kencochrane.raven.integration;

import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryApi;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link net.kencochrane.raven.Client}.
 */
public class ClientTest {

    private SentryApi api;

    @Test
    public void captureMessage_http() throws IOException, InterruptedException {
        Client client = new Client(IntegrationContext.httpDsn);
        String message = ClientTest.class.getName() + ".captureMessage_http says hi!";
        captureMessage(client, message, false);
    }

    @Test
    public void captureMessage_withTags() throws IOException, InterruptedException {
        Client client = new Client(IntegrationContext.httpDsn);
        String message = ClientTest.class.getName() + ".captureMessage_withTags says hi!";
        Map<String, Object> tags = new HashMap<String, Object>();
        tags.put("release", "1.2.0");
        tags.put("uptime", 60000L);
        captureMessage(client, message, false, tags);
    }

    @Test
    public void captureMessage_udp() throws IOException, InterruptedException {
        Client client = new Client(IntegrationContext.udpDsn);
        String message = ClientTest.class.getName() + ".captureMessage_udp says hi!";
        captureMessage(client, message, true);
    }

    @Test
    public void captureMessage_complex_http() throws IOException {
        Client client = new Client(IntegrationContext.httpDsn);
        final String message = ClientTest.class.getName() + ".captureMessage_complex_http says hi!";
        final String logger = "some.custom.logger.Name";
        final String culprit = "Damn you!";
        client.captureMessage(message, null, logger, null, culprit);

        List<SentryApi.Event> events = api.getEvents(IntegrationContext.projectSlug);
        assertEquals(1, events.size());
        SentryApi.Event event = events.get(0);
        assertTrue(event.count > 0);
        assertEquals(Client.Default.LOG_LEVEL, event.level);
        assertEquals(message, event.message);
        assertEquals(culprit, event.title);
        assertEquals(logger, event.logger);
    }

    @Test
    public void captureMessage_complex_udp() throws IOException, InterruptedException {
        Client client = new Client(IntegrationContext.udpDsn);
        final String message = ClientTest.class.getName() + ".captureMessage_complex_udp says hi!";
        final String logger = "some.custom.logger.Name";
        final String culprit = "Damn you!";
        client.captureMessage(message, null, logger, null, culprit);
        Thread.sleep(1000);

        List<SentryApi.Event> events = api.getEvents(IntegrationContext.projectSlug);
        assertEquals(1, events.size());
        SentryApi.Event event = events.get(0);
        assertTrue(event.count > 0);
        assertEquals(Client.Default.LOG_LEVEL, event.level);
        assertEquals(message, event.message);
        assertEquals(culprit, event.title);
        assertEquals(logger, event.logger);
    }

    protected void captureMessage(Client client, String message, boolean wait) throws IOException, InterruptedException {
        captureMessage(client, message, wait, null);
    }

    protected void captureMessage(Client client, String message, boolean wait, Map<String, ?> tags) throws IOException, InterruptedException {
        client.captureMessage(message, tags);
        if (wait) {
            // Wait a bit in case of UDP transport
            Thread.sleep(1000);
        }
        List<SentryApi.Event> events = api.getEvents(IntegrationContext.projectSlug);
        assertEquals(1, events.size());
        SentryApi.Event event = events.get(0);
        assertTrue(event.count > 0);
        assertEquals(Client.Default.LOG_LEVEL, event.level);
        assertEquals(message, event.message);
        assertEquals(message, event.title);
        assertEquals(Client.Default.LOGGER, event.logger);

        // Log the same message; the count should be incremented
        client.captureMessage(message, tags);
        if (wait) {
            Thread.sleep(1000);
        }
        events = api.getEvents(IntegrationContext.projectSlug);
        assertEquals(1, events.size());
        SentryApi.Event newEvent = events.get(0);
        assertEquals(event.count + 1, newEvent.count);
        assertEquals(Client.Default.LOG_LEVEL, event.level);
        assertEquals(message, event.message);
        assertEquals(message, event.title);
        assertEquals(Client.Default.LOGGER, event.logger);

        // TODO Verify tags when it's a bit more stable on the Sentry side
    }

    @Before
    public void setUp() throws IOException {
        IntegrationContext.init();
        api = IntegrationContext.api;
        api.clear(IntegrationContext.httpDsn.projectId);
    }

}

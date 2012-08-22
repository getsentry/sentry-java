package net.kencochrane.sentry;

import java.util.Date;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * This test validates JSON message bodies created by RavenClient.
 * 
 * @author vvasabi
 */
public class SentyClientJsonTest extends RavenClient {

    public SentyClientJsonTest() {
        super("http://public:secret@example.com/path/1");
    }

    @Test
    public void testMessageInterfaceObjectAdded() {
        String messageString = "Test";
        String timestamp = RavenUtils.getTimestampString(new Date().getTime());
        String logger = "sentry.test";
        JSONObject result = buildJSON(messageString, timestamp, logger, 20, messageString, null);
        assertTrue(result.containsKey("sentry.interfaces.Message"));
        
        JSONObject message = (JSONObject)result.get("sentry.interfaces.Message");
        assertEquals(message.get("message"), messageString);
        assertTrue(message.get("params") instanceof JSONArray);
    }

    @Test
    public void testMessageInterfaceObjectNotAdded() {
        // when there is an exception, Message interface object should not be added
        String messageString = "Test";
        String timestamp = RavenUtils.getTimestampString(new Date().getTime());
        String logger = "sentry.test";
        Throwable exception = new Exception("Test");
        JSONObject result = buildJSON(messageString, timestamp, logger, 20, messageString, exception);
        assertFalse(result.containsKey("sentry.interfaces.Message"));
    }

}

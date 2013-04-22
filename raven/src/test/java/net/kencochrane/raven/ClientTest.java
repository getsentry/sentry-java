package net.kencochrane.raven;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * A test extending the concrete implementation seems a bit dubious to me but it's what's being done in JSONProcessorTest.
 *
 * @author oliver.henlich
 */
public class ClientTest extends Client {

    public ClientTest() {
        super(SentryDsn.build("http://public:private@localhost:9000/1"));
    }

    @Test
    public void testBuildMessage_messageWithoutParameters() {

        Message message = buildMessage(
                "test a=param1, b=param2",
                formatTimestamp(new Date().getTime()),
                "test",
                Events.LogLevel.ERROR.intValue,
                "test",
                null,
                null,
                null,
                null);


        assertEquals("test a=param1, b=param2", message.json.get("message"));

        JSONObject jsonMessage = (JSONObject) message.json.get("sentry.interfaces.Message");
        assertEquals("test a=param1, b=param2", jsonMessage.get("message"));
        assertEquals(new JSONArray(), jsonMessage.get("params"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBuildMessage_messageWithParameters() {

        Message message = buildMessage(
                "test a=param1, b=param2",
                formatTimestamp(new Date().getTime()),
                "test",
                Events.LogLevel.ERROR.intValue,
                "test",
                null,
                null,
                "test a={}, b={}",
                Arrays.asList("param1", "param2"));


        assertEquals("test a=param1, b=param2", message.json.get("message"));

        JSONObject jsonMessage = (JSONObject) message.json.get("sentry.interfaces.Message");
        assertEquals("test a={}, b={}", jsonMessage.get("message"));
        JSONArray expectedParamList = new JSONArray();
        expectedParamList.add("param1");
        expectedParamList.add("param2");
        assertEquals(expectedParamList, jsonMessage.get("params"));
    }
}

package net.kencochrane.raven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.kencochrane.raven.Events.LogLevel;
import net.kencochrane.raven.spi.JSONProcessor;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * This test ensures that {@link Client} would execute the
 * {@link net.kencochrane.raven.spi.JSONProcessor} passed in.
 *
 * @author vvasabi
 */
public class JSONProcessorTest extends Client {

    public JSONProcessorTest() {
        super(SentryDsn.build("http://public:private@localhost:9000/1"));
    }

    @After
    public void tearDown() {
        setJSONProcessors(Collections.<JSONProcessor>emptyList());
    }

    @Test
    public void testWithProcessor() {
        List<JSONProcessor> processors = new ArrayList<JSONProcessor>();
        JSONProcessor mockProcessor = new MockJSONProcessor();
        processors.add(mockProcessor);
        setJSONProcessors(processors);

        mockProcessor.prepareDiagnosticContext();
        Message message = buildMessage("test",
            formatTimestamp(new Date().getTime()), "test",
            LogLevel.ERROR.intValue, "test", null, null);
        assertEquals("Value", message.json.get("Test"));
    }

    private static class MockJSONProcessor implements JSONProcessor {

        private String testValue;

        @Override
        public void prepareDiagnosticContext() {
            testValue = "Value";
        }

        @Override
        public void clearDiagnosticContext() {
            testValue = null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void process(JSONObject json, Throwable exception) {
            json.put("Test", testValue);
        }

    }

}

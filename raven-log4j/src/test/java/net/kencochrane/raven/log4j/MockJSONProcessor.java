package net.kencochrane.raven.log4j;

import org.json.simple.JSONObject;

import net.kencochrane.raven.spi.JSONProcessor;

public class MockJSONProcessor implements JSONProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(JSONObject json) {
        json.put("Test", "Value");
    }

}

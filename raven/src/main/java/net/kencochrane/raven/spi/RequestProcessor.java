package net.kencochrane.raven.spi;

import org.json.simple.JSONObject;

public interface RequestProcessor {

    void process(JSONObject json);

}

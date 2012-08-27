package net.kencochrane.raven.spi;

import org.json.simple.JSONObject;

/**
 * A JSONProcessor is used to modify JSON requests before they are sent to
 * Sentry. It is expected for a JSON processor to be singleton and be
 * thread-safe.
 *
 * To register JSONProcessors, refer to the settings of the appender used.
 *
 * @author vvasabi
 * @since 1.0
 */
public interface JSONProcessor {

    /**
     * Modify the JSON request object specified before it is sent to Sentry.
     * This method may be called concurrently and therefore must be thread-safe.
     *
     * @param json request JSON object to be modified
     */
    void process(JSONObject json);

}

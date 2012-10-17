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
     * This is called when a message is logged. Since
     * {@link #process(JSONObject, Throwable)} may be executed on a different
     * thread, this method should copy any data the processor needs into
     * {@link RavenMDC}.
     *
     * For each message logged, this method should be called exactly once.
     */
    void prepareDiagnosticContext();

    /**
     * This is called after the message logged is processed (in synchronous
     * mode), or after the message has been sent to the processing queue (in
     * asynchronous mode). The intention of this method is to clear the data
     * put in {@link RavenMDC} by {@link #prepareDiagnosticContext()} to prevent
     * memory leak.
     *
     * For each message logged, this method should be called exactly once.
     */
    void clearDiagnosticContext();

    /**
     * Modify the JSON request object specified before it is sent to Sentry.
     * This method may be called concurrently and therefore must be thread-safe.
     *
     * @param json request JSON object to be modified
     * @param exception exception attached with the message and may be null
     */
    void process(JSONObject json, Throwable exception);

}

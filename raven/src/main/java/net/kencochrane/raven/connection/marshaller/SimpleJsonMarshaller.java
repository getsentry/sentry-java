package net.kencochrane.raven.connection.marshaller;

import net.kencochrane.raven.event.LoggedEvent;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;

/**
 * Marshaller allowing to transform a simple {@link LoggedEvent} into a JSON String send over a stream.
 */
public class SimpleJsonMarshaller implements Marshaller {
    /**
     * Hexadecimal string representing a uuid4 value.
     */
    public static final String EVENT_ID = "event_id";
    /**
     * User-readable representation of this event.
     */
    public static final String MESSAGE = "message";
    /**
     * Indicates when the logging record was created.
     */
    public static final String TIMESTAMP = "timestamp";
    /**
     * The record severity.
     */
    public static final String LEVEL = "level";
    /**
     * The name of the logger which created the record.
     */
    public static final String LOGGER = "logger";
    /**
     * A string representing the platform the client is submitting from.
     */
    public static final String PLATFORM = "platform";
    /**
     * Function call which was the primary perpetrator of this event.
     */
    public static final String CULPRIT = "culprit";
    /**
     * A map or list of tags for this event.
     */
    public static final String TAGS = "tags";
    /**
     * Identifies the host client from which the event was recorded.
     */
    public static final String SERVER_NAME = "server_name";
    /**
     * A list of relevant modules and their versions.
     */
    public static final String MODULES = "modules";
    /**
     * An arbitrary mapping of additional metadata to store with the event.
     */
    public static final String EXTRA = "extra";
    /**
     * Checksum for the event, allowing to group events with a similar checksum.
     */
    public static final String CHECKSUM = "checksum";
    /**
     * Maximum length for a message.
     */
    public static final int MAX_MESSAGE_LENGTH = 1000;
    /**
     * Date format for ISO 8601
     */
    private static final DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");

    @Override
    public void marshall(LoggedEvent event, OutputStream destination) throws IOException {
        JSONObject jsonObject = encodeToJSONObject(event);
        jsonObject.writeJSONString(new OutputStreamWriter(destination));
    }

    @SuppressWarnings("unchecked")
    private JSONObject encodeToJSONObject(LoggedEvent event) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(EVENT_ID, formatId(event.getId()));
        jsonObject.put(MESSAGE, formatMessage(event.getMessage()));
        jsonObject.put(TIMESTAMP, formatTimestamp(event.getTimestamp()));
        jsonObject.put(LEVEL, formatLevel(event.getLevel()));
        jsonObject.put(LOGGER, event.getLogger());
        jsonObject.put(PLATFORM, event.getPlatform());
        jsonObject.put(CULPRIT, event.getCulprit());
        jsonObject.put(TAGS, event.getTags());
        jsonObject.put(SERVER_NAME, event.getServerName());
        jsonObject.put(CHECKSUM, event.getChecksum());

        for (Map.Entry<String, SentryInterface> sentryInterfaceEntry : event.getSentryInterfaces().entrySet()) {
            //TODO: Usually we would look for marshallers for each interface type.
            jsonObject.put(sentryInterfaceEntry.getKey(), sentryInterfaceEntry.getValue());
        }

        return jsonObject;
    }

    /**
     * Formats a message, ensuring that the maximum length {@link #MAX_MESSAGE_LENGTH} isn't reached.
     *
     * @param message message to format.
     * @return formatted message (shortened if necessary).
     */
    private String formatMessage(String message) {
        return (message.length() > MAX_MESSAGE_LENGTH) ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }

    /**
     * Formats the {@code UUID} to send only the 32 necessary characters.
     *
     * @param id uuid to format.
     * @return a {@code UUID} stripped from the "-" characters.
     */
    private String formatId(UUID id) {
        return id.toString().replaceAll("-", "");
    }

    /**
     * Formats a log level into one of the accepted string representation of a log level.
     *
     * @param level log level to format.
     * @return log level as a String.
     */
    private String formatLevel(LoggedEvent.Level level) {
        switch (level) {
            case DEBUG:
                return "debug";
            case FATAL:
                return "fatal";
            case WARNING:
                return "warning";
            case INFO:
                return "info";
            case ERROR:
            default:
                return "error";
        }
    }

    /**
     * Formats a timestamp in the ISO-8601 format without timezone.
     *
     * @param timestamp date to format.
     * @return timestamp as a formatted String.
     */
    private String formatTimestamp(Date timestamp) {
        return isoFormat.format(timestamp);
    }
}

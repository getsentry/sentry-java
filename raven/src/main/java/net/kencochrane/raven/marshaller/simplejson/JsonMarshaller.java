package net.kencochrane.raven.marshaller.simplejson;

import net.kencochrane.raven.event.LoggedEvent;
import net.kencochrane.raven.event.interfaces.*;
import net.kencochrane.raven.marshaller.Marshaller;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;

/**
 * Marshaller allowing to transform a simple {@link LoggedEvent} into a compressed JSON String send over a stream.
 */
public class JsonMarshaller implements Marshaller {
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
    private static final Logger logger = Logger.getLogger(JsonMarshaller.class.getCanonicalName());
    /**
     * Date format for ISO 8601.
     */
    private static final DateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
    private final Map<Class<? extends SentryInterface>, InterfaceMarshaller> interfaceMarshallers =
            new HashMap<Class<? extends SentryInterface>, InterfaceMarshaller>();
    /**
     * Enables disables the compression of JSON.
     */
    private boolean compression = true;
    /**
     * Charset used to transmit data.
     */
    private Charset charset = Charset.defaultCharset();

    {
        interfaceMarshallers.put(ExceptionInterface.class, new ExceptionMarshaller());
        interfaceMarshallers.put(HttpInterface.class, new HttpMarshaller());
        interfaceMarshallers.put(MessageInterface.class, new MessageMarshaller());
        interfaceMarshallers.put(StackTraceInterface.class, new StackTraceMarshaller());
    }

    @Override
    public void marshall(LoggedEvent event, OutputStream destination) {
        OutputStream outputStream = new Base64OutputStream(destination);
        if (compression)
            outputStream = new DeflaterOutputStream(outputStream);

        Writer writer = new OutputStreamWriter(outputStream, charset);
        JSONObject jsonObject = encodeToJSONObject(event);
        try {
            jsonObject.writeJSONString(writer);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An exception occurred serialising the event.", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
            }
        }
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
        jsonObject.put(EXTRA, event.getExtra());
        jsonObject.put(CHECKSUM, event.getChecksum());

        for (Map.Entry<String, SentryInterface> sentryInterfaceEntry : event.getSentryInterfaces().entrySet()) {
            jsonObject.put(sentryInterfaceEntry.getKey(), formatInterface(sentryInterfaceEntry.getValue()));
        }

        return jsonObject;
    }

    private JSONObject formatInterface(SentryInterface sentryInterface) {
        InterfaceMarshaller interfaceMarshaller = interfaceMarshallers.get(sentryInterface.getClass());
        if (interfaceMarshaller != null) {
            return interfaceMarshaller.serialiseInterface(sentryInterface);
        } else {
            return new JSONObject();
        }
    }

    /**
     * Formats a message, ensuring that the maximum length {@link #MAX_MESSAGE_LENGTH} isn't reached.
     *
     * @param message message to format.
     * @return formatted message (shortened if necessary).
     */
    private String formatMessage(String message) {
        if (message == null)
            return null;
        else if (message.length() > MAX_MESSAGE_LENGTH)
            return message.substring(0, MAX_MESSAGE_LENGTH);
        else return message;
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
        if (level == null)
            return null;

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
                return "error";
            default:
                return null;
        }
    }

    /**
     * Formats a timestamp in the ISO-8601 format without timezone.
     *
     * @param timestamp date to format.
     * @return timestamp as a formatted String.
     */
    private String formatTimestamp(Date timestamp) {
        return ISO_FORMAT.format(timestamp);
    }

    /**
     * Enables the JSON compression with GZip.
     *
     * @param compression state of the compression.
     */
    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}

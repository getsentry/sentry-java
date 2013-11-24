package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Charsets;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import net.kencochrane.raven.marshaller.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import static com.google.common.io.BaseEncoding.base64;

/**
 * Event marshaller using JSON to send the data.
 * <p>
 * The content can also be compressed with {@link DeflaterOutputStream} in which case the binary result is encoded
 * in base 64.
 * </p>
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
    /**
     * Date format for ISO 8601.
     */
    private static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final Logger logger = LoggerFactory.getLogger(JsonMarshaller.class);
    private final JsonFactory jsonFactory = new JsonFactory();
    private final Map<Class<? extends SentryInterface>, InterfaceBinding> interfaceBindings =
            new HashMap<Class<? extends SentryInterface>, InterfaceBinding>();
    /**
     * Enables disables the compression of JSON.
     */
    private boolean compression = true;

    @Override
    public void marshall(Event event, OutputStream destination) {
        // Prevent the stream from being closed automatically
        destination = new UncloseableOutputStream(destination);

        if (compression)
            destination = new DeflaterOutputStream(base64().encodingStream(
                    new OutputStreamWriter(destination, Charsets.UTF_8)));

        JsonGenerator generator = null;
        try {
            generator = jsonFactory.createGenerator(destination);
            writeContent(generator, event);
        } catch (IOException e) {
            logger.error("An exception occurred while serialising the event.", e);
        } finally {
            try {
                if (generator != null)
                    generator.close();
            } catch (IOException e) {
                logger.error("An exception occurred while closing the json stream.", e);
            }
        }
    }

    private void writeContent(JsonGenerator generator, Event event) throws IOException {
        generator.writeStartObject();

        generator.writeStringField(EVENT_ID, formatId(event.getId()));
        generator.writeStringField(MESSAGE, formatMessage(event.getMessage()));
        generator.writeStringField(TIMESTAMP, formatTimestamp(event.getTimestamp()));
        generator.writeStringField(LEVEL, formatLevel(event.getLevel()));
        generator.writeStringField(LOGGER, event.getLogger());
        generator.writeStringField(PLATFORM, event.getPlatform());
        generator.writeStringField(CULPRIT, event.getCulprit());
        writeTags(generator, event.getTags());
        generator.writeStringField(SERVER_NAME, event.getServerName());
        writeExtras(generator, event.getExtra());
        generator.writeStringField(CHECKSUM, event.getChecksum());
        writeInterfaces(generator, event.getSentryInterfaces());

        generator.writeEndObject();
    }

    @SuppressWarnings("unchecked")
    private void writeInterfaces(JsonGenerator generator, Map<String, SentryInterface> sentryInterfaces)
            throws IOException {
        for (Map.Entry<String, SentryInterface> interfaceEntry : sentryInterfaces.entrySet()) {
            SentryInterface sentryInterface = interfaceEntry.getValue();

            if (interfaceBindings.containsKey(sentryInterface.getClass())) {
                generator.writeFieldName(interfaceEntry.getKey());
                interfaceBindings.get(sentryInterface.getClass()).writeInterface(generator, sentryInterface);
            } else {
                logger.error("Couldn't parse the content of '{}' provided in {}.",
                        interfaceEntry.getKey(), sentryInterface);
            }
        }
    }

    private void writeExtras(JsonGenerator generator, Map<String, Object> extras) throws IOException {
        generator.writeObjectFieldStart(EXTRA);
        for (Map.Entry<String, Object> extra : extras.entrySet()) {
            generator.writeFieldName(extra.getKey());
            safelyWriteObject(generator, extra.getValue());
        }
        generator.writeEndObject();
    }

    private void safelyWriteObject(JsonGenerator generator, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            value = Arrays.asList((Object[]) value);
        }

        if (value instanceof Iterable) {
            generator.writeStartArray();
            for (Object subValue : (Iterable<?>) value) {
                generator.writeObject(subValue);
            }
            generator.writeEndArray();
        } else if (value instanceof Map) {
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null)
                    generator.writeFieldName("null");
                else
                    generator.writeFieldName(entry.getKey().toString());
                generator.writeObject(entry.getValue());
            }
            generator.writeEndObject();
        } else if (value == null) {
            generator.writeNull();
        } else {
            generator.writeObject(value);
        }
    }

    private void writeTags(JsonGenerator generator, Map<String, String> tags) throws IOException {
        generator.writeObjectFieldStart(TAGS);
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            generator.writeStringField(tag.getKey(), tag.getValue());
        }
        generator.writeEndObject();
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
    private String formatLevel(Event.Level level) {
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
                logger.warn("The level '{}' isn't supported, this should NEVER happen, contact Raven developers",
                        level.name());
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
        DateFormat isoFormat = new SimpleDateFormat(ISO_8601_FORMAT);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return isoFormat.format(timestamp);
    }

    /**
     * Add an interface binding to send a type of {@link SentryInterface} through a JSON stream.
     *
     * @param sentryInterfaceClass Actual type of SentryInterface supported by the {@link InterfaceBinding}
     * @param binding              InterfaceBinding converting SentryInterfaces of type {@code sentryInterfaceClass}.
     * @param <T>                  Type of SentryInterface.
     */
    public <T extends SentryInterface> void addInterfaceBinding(Class<T> sentryInterfaceClass,
                                                                InterfaceBinding<T> binding) {
        this.interfaceBindings.put(sentryInterfaceClass, binding);
    }

    /**
     * Enables the JSON compression with deflate.
     *
     * @param compression state of the compression.
     */
    public void setCompression(boolean compression) {
        this.compression = compression;
    }
}

package com.getsentry.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.util.Base64;
import com.getsentry.raven.util.Base64OutputStream;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.interfaces.SentryInterface;
import com.getsentry.raven.marshaller.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

/**
 * Event marshaller using JSON to send the data.
 * <p>
 * The content can also be compressed with {@link DeflaterOutputStream} in which case the binary result is encoded
 * in base 64.
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
     * An object representing the SDK name and version.
     */
    public static final String SDK = "sdk";
    /**
     * A map or list of tags for this event.
     */
    public static final String TAGS = "tags";
    /**
     * List of breadcrumbs for this event.
     */
    public static final String BREADCRUMBS = "breadcrumbs";
    /**
     * Identifies the host client from which the event was recorded.
     */
    public static final String SERVER_NAME = "server_name";
    /**
     * Identifies the the version of the application.
     */
    public static final String RELEASE = "release";
    /**
     * Identifies the environment the application is running in.
     */
    public static final String ENVIRONMENT = "environment";
    /**
     * Event fingerprint, a list of strings used to dictate the deduplicating for this event.
     */
    public static final String FINGERPRINT = "fingerprint";
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
     * Default maximum length for a message.
     */
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 1000;
    /**
     * Date format for ISO 8601.
     */
    private static final ThreadLocal<DateFormat> ISO_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat;
        }
    };

    private static final Logger logger = LoggerFactory.getLogger(JsonMarshaller.class);
    private final JsonFactory jsonFactory = new JsonFactory();
    private final Map<Class<? extends SentryInterface>, InterfaceBinding<?>> interfaceBindings = new HashMap<>();
    /**
     * Enables disables the compression of JSON.
     */
    private boolean compression = true;
    /**
     * Maximum length for a message.
     */
    private final int maxMessageLength;

    /**
     * Create instance of JsonMarshaller with default message length.
     */
    public JsonMarshaller() {
        maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
    }

    /**
     * Create instance of JsonMarshaller with provided the maximum length of the messages.
     *
     * @param maxMessageLength the maximum message length
     */
    public JsonMarshaller(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    @Override
    public void marshall(Event event, OutputStream destination) {
        // Prevent the stream from being closed automatically
        destination = new UncloseableOutputStream(destination);

        if (compression) {
            destination = new DeflaterOutputStream(new Base64OutputStream(destination, Base64.NO_WRAP));
        }

        try (JsonGenerator generator = jsonFactory.createGenerator(destination)) {
            writeContent(generator, event);
        } catch (IOException e) {
            logger.error("An exception occurred while serialising the event.", e);
        } finally {
            try {
                destination.close();
            } catch (IOException e) {
                logger.error("An exception occurred while serialising the event.", e);
            }
        }
    }

    private void writeContent(JsonGenerator generator, Event event) throws IOException {
        generator.writeStartObject();

        generator.writeStringField(EVENT_ID, formatId(event.getId()));
        generator.writeStringField(MESSAGE, trimMessage(event.getMessage()));
        generator.writeStringField(TIMESTAMP, ISO_FORMAT.get().format(event.getTimestamp()));
        generator.writeStringField(LEVEL, formatLevel(event.getLevel()));
        generator.writeStringField(LOGGER, event.getLogger());
        generator.writeStringField(PLATFORM, event.getPlatform());
        generator.writeStringField(CULPRIT, event.getCulprit());
        writeSdk(generator, event.getSdkName(), event.getSdkVersion());
        writeTags(generator, event.getTags());
        writeBreadcumbs(generator, event.getBreadcrumbs());
        generator.writeStringField(SERVER_NAME, event.getServerName());
        generator.writeStringField(RELEASE, event.getRelease());
        generator.writeStringField(ENVIRONMENT, event.getEnvironment());
        writeExtras(generator, event.getExtra());
        writeCollection(generator, FINGERPRINT, event.getFingerprint());
        generator.writeStringField(CHECKSUM, event.getChecksum());
        writeInterfaces(generator, event.getSentryInterfaces());

        generator.writeEndObject();
    }

    private void writeInterfaces(JsonGenerator generator, Map<String, SentryInterface> sentryInterfaces)
        throws IOException {
        for (Map.Entry<String, SentryInterface> interfaceEntry : sentryInterfaces.entrySet()) {
            SentryInterface sentryInterface = interfaceEntry.getValue();

            if (interfaceBindings.containsKey(sentryInterface.getClass())) {
                generator.writeFieldName(interfaceEntry.getKey());
                getInterfaceBinding(sentryInterface).writeInterface(generator, interfaceEntry.getValue());
            } else {
                logger.error("Couldn't parse the content of '{}' provided in {}.",
                    interfaceEntry.getKey(), sentryInterface);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends SentryInterface> InterfaceBinding<? super T> getInterfaceBinding(T sentryInterface) {
        // Reduces the @SuppressWarnings to a oneliner
        return (InterfaceBinding<? super T>) interfaceBindings.get(sentryInterface.getClass());
    }

    private void writeExtras(JsonGenerator generator, Map<String, Object> extras) throws IOException {
        generator.writeObjectFieldStart(EXTRA);
        for (Map.Entry<String, Object> extra : extras.entrySet()) {
            generator.writeFieldName(extra.getKey());
            safelyWriteObject(generator, extra.getValue());
        }
        generator.writeEndObject();
    }

    private void writeCollection(JsonGenerator generator, String name, Collection<String> value) throws IOException {
        if (value != null && !value.isEmpty()) {
            generator.writeArrayFieldStart(name);
            for (String element: value) {
                generator.writeString(element);
            }
            generator.writeEndArray();
        }
    }

    private void safelyWriteObject(JsonGenerator generator, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            value = Arrays.asList((Object[]) value);
        }

        if (value instanceof Iterable) {
            generator.writeStartArray();
            for (Object subValue : (Iterable<?>) value) {
                safelyWriteObject(generator, subValue);
            }
            generator.writeEndArray();
        } else if (value instanceof Map) {
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) {
                    generator.writeFieldName("null");
                } else {
                    generator.writeFieldName(entry.getKey().toString());
                }
                safelyWriteObject(generator, entry.getValue());
            }
            generator.writeEndObject();
        } else if (value == null) {
            generator.writeNull();
        } else {
            try {
                /** @see com.fasterxml.jackson.core.JsonGenerator#_writeSimpleObject(Object)  */
                generator.writeObject(value);
            } catch (IllegalStateException e) {
                logger.debug("Couldn't marshal '{}' of type '{}', had to be converted into a String",
                    value, value.getClass());
                generator.writeString(value.toString());
            }
        }
    }

    private void writeSdk(JsonGenerator generator, String sdkName, String sdkVersion) throws IOException {
        generator.writeObjectFieldStart(SDK);
        generator.writeStringField("name", sdkName);
        generator.writeStringField("version", sdkVersion);
        generator.writeEndObject();
    }

    private void writeTags(JsonGenerator generator, Map<String, String> tags) throws IOException {
        generator.writeObjectFieldStart(TAGS);
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            generator.writeStringField(tag.getKey(), tag.getValue());
        }
        generator.writeEndObject();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void writeBreadcumbs(JsonGenerator generator, List<Breadcrumb> breadcrumbs) throws IOException {
        if (breadcrumbs.size() < 1) {
            return;
        }

        generator.writeObjectFieldStart(BREADCRUMBS);
        generator.writeArrayFieldStart("values");
        for (Breadcrumb breadcrumb : breadcrumbs) {
            generator.writeStartObject();
            // getTime() returns ts in millis, but breadcrumbs expect seconds
            generator.writeNumberField("timestamp", breadcrumb.getTimestamp().getTime() / 1000);

            if (breadcrumb.getType() != null) {
                generator.writeStringField("type", breadcrumb.getType());
            }
            if (breadcrumb.getLevel() != null) {
                generator.writeStringField("level", breadcrumb.getLevel());
            }
            if (breadcrumb.getMessage() != null) {
                generator.writeStringField("message", breadcrumb.getMessage());
            }
            if (breadcrumb.getCategory() != null) {
                generator.writeStringField("category", breadcrumb.getCategory());
            }
            if (breadcrumb.getData() != null && breadcrumb.getData().size() > 0) {
                generator.writeObjectFieldStart("data");
                for (Map.Entry<String, String> entry : breadcrumb.getData().entrySet()) {
                    generator.writeStringField(entry.getKey(), entry.getValue());
                }
                generator.writeEndObject();
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /**
     * Trims a message, ensuring that the maximum length {@link #maxMessageLength} isn't reached.
     *
     * @param message message to format.
     * @return trimmed message (shortened if necessary).
     */
    private String trimMessage(String message) {
        if (message == null) {
            return null;
        } else if (message.length() > maxMessageLength) {
            return message.substring(0, maxMessageLength);
        } else {
            return message;
        }
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
        if (level == null) {
            return null;
        }

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
                logger.error("The level '{}' isn't supported, this should NEVER happen, contact Raven developers",
                    level.name());
                return null;
        }
    }

    /**
     * Add an interface binding to send a type of {@link SentryInterface} through a JSON stream.
     *
     * @param sentryInterfaceClass Actual type of SentryInterface supported by the {@link InterfaceBinding}
     * @param binding              InterfaceBinding converting SentryInterfaces of type {@code sentryInterfaceClass}.
     * @param <T>                  Type of SentryInterface received by the InterfaceBinding.
     * @param <F>                  Type of the interface stored in the event to send to the InterfaceBinding.
     */
    public <T extends SentryInterface, F extends T> void addInterfaceBinding(Class<F> sentryInterfaceClass,
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

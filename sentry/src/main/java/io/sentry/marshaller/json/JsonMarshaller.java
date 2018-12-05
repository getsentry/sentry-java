package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.Breadcrumb;
import io.sentry.event.Event;
import io.sentry.event.Sdk;
import io.sentry.event.interfaces.SentryInterface;
import io.sentry.marshaller.Marshaller;
import io.sentry.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Event marshaller using JSON to send the data.
 * <p>
 * The content can also be compressed with {@link GZIPOutputStream}.
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
     * Name of the transaction that this event occurred inside of.
     */
    public static final String TRANSACTION = "transaction";
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
     * Map of map of contexts for this event.
     */
    public static final String CONTEXTS = "contexts";
    /**
     * Identifies the host client from which the event was recorded.
     */
    public static final String SERVER_NAME = "server_name";
    /**
     * Identifies the the version of the application.
     */
    public static final String RELEASE = "release";
    /**
     * Identifies the the distribution of the application.
     */
    public static final String DIST = "dist";
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
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
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
        this(DEFAULT_MAX_MESSAGE_LENGTH);
    }

    /**
     * Create instance of JsonMarshaller with provided maximum length of the messages.
     *
     * @param maxMessageLength the maximum message length
     */
    public JsonMarshaller(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    @Override
    public void marshall(Event event, OutputStream destination) throws IOException {
        // Prevent the stream from being closed automatically
        destination = new UncloseableOutputStream(destination);

        if (compression) {
            destination = new GZIPOutputStream(destination);
        }

        try (JsonGenerator generator = createJsonGenerator(destination)) {
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

    /**
     * Creates the {@link JsonGenerator} used to marshall to json.
     * This method makes it easier to provide a custom implementation.
     *
     * @param destination used to read the content
     * @return a new instance
     * @throws IOException on error reading the stream
     */
    @SuppressWarnings("WeakerAccess")
    protected JsonGenerator createJsonGenerator(OutputStream destination) throws IOException {
        return new SentryJsonGenerator(jsonFactory.createGenerator(destination));
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getContentEncoding() {
        if (isCompressed()) {
            return "gzip";
        }
        return null;
    }

    private void writeContent(JsonGenerator generator, Event event) throws IOException {
        generator.writeStartObject();

        generator.writeStringField(EVENT_ID, formatId(event.getId()));
        generator.writeStringField(MESSAGE, Util.trimString(event.getMessage(), maxMessageLength));
        generator.writeStringField(TIMESTAMP, ISO_FORMAT.get().format(event.getTimestamp()));
        generator.writeStringField(LEVEL, formatLevel(event.getLevel()));
        generator.writeStringField(LOGGER, event.getLogger());
        generator.writeStringField(PLATFORM, event.getPlatform());
        generator.writeStringField(CULPRIT, event.getCulprit());
        generator.writeStringField(TRANSACTION, event.getTransaction());
        writeSdk(generator, event.getSdk());
        writeTags(generator, event.getTags());
        writeBreadcumbs(generator, event.getBreadcrumbs());
        writeContexts(generator, event.getContexts());
        generator.writeStringField(SERVER_NAME, event.getServerName());
        generator.writeStringField(RELEASE, event.getRelease());
        generator.writeStringField(DIST, event.getDist());
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
            generator.writeObject(extra.getValue());
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

    private void writeSdk(JsonGenerator generator, Sdk sdk) throws IOException {
        generator.writeObjectFieldStart(SDK);
        generator.writeStringField("name", sdk.getName());
        generator.writeStringField("version", sdk.getVersion());
        if (sdk.getIntegrations() != null && !sdk.getIntegrations().isEmpty()) {
            generator.writeArrayFieldStart("integrations");
            for (String integration : sdk.getIntegrations()) {
                generator.writeString(integration);
            }
            generator.writeEndArray();
        }
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
        if (breadcrumbs.isEmpty()) {
            return;
        }

        generator.writeObjectFieldStart(BREADCRUMBS);
        generator.writeArrayFieldStart("values");
        for (Breadcrumb breadcrumb : breadcrumbs) {
            generator.writeStartObject();
            // getTime() returns ts in millis, but breadcrumbs expect seconds
            generator.writeNumberField("timestamp", breadcrumb.getTimestamp().getTime() / 1000);

            if (breadcrumb.getType() != null) {
                generator.writeStringField("type", breadcrumb.getType().getValue());
            }
            if (breadcrumb.getLevel() != null) {
                generator.writeStringField("level", breadcrumb.getLevel().getValue());
            }
            if (breadcrumb.getMessage() != null) {
                generator.writeStringField("message", breadcrumb.getMessage());
            }
            if (breadcrumb.getCategory() != null) {
                generator.writeStringField("category", breadcrumb.getCategory());
            }
            if (breadcrumb.getData() != null && !breadcrumb.getData().isEmpty()) {
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

    private void writeContexts(JsonGenerator generator, Map<String, Map<String, Object>> contexts) throws IOException {
        if (contexts.isEmpty()) {
            return;
        }

        generator.writeObjectFieldStart(CONTEXTS);
        for (Map.Entry<String, Map<String, Object>> contextEntry : contexts.entrySet()) {
            generator.writeObjectFieldStart(contextEntry.getKey());
            for (Map.Entry<String, Object> innerContextEntry : contextEntry.getValue().entrySet()) {
                generator.writeObjectField(innerContextEntry.getKey(), innerContextEntry.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();
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
                logger.error("The level '{}' isn't supported, this should NEVER happen, contact Sentry developers",
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
     * Enables the JSON compression with gzip.
     *
     * @param compression state of the compression.
     */
    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public boolean isCompressed() {
        return compression;
    }
}

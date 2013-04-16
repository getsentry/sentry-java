//CHECKSTYLE.OFF: .*

package net.kencochrane.raven;

import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.event.helper.EventBuilderHelper;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.time.DateFormatUtils;
import org.json.simple.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * Raven client for Java, allowing sending of messages to Sentry.
 * <p>
 * Clients will typically automatically start the underlying transport layer once instantiated. The default client
 * configuration relies on the following mapping of schemes to transport classes:
 * </p>
 * <ul>
 * <li>http:</li>
 * <li>https:</li>
 * <li>naive+https</li>
 * <li>udp</li>
 * </ul>
 * <p>
 * </p>
 *
 * @deprecated Use {@link Raven} instead.
 */
@Deprecated
public class Client {

    private final Raven raven;

    @Deprecated
    public interface Default {
        @Deprecated
        String LOGGER = "root";
        @Deprecated
        int LOG_LEVEL = 5000;
        @Deprecated
        String EMPTY_MESSAGE = "(empty)";
    }

    /**
     * Async transport layers require some extra work when instantiating.
     */
    @Deprecated
    public static final String VARIANT_ASYNC = "async";

    /**
     * The registry mapping schemes to transport layer classes.
     */
    @Deprecated
    protected static final Map<String, Class> TRANSPORT_REGISTRY = new HashMap<String, Class>();

    /**
     * The dsn used by this client.
     */
    @Deprecated
    public final SentryDsn dsn = null;

    /**
     * Whether messages should be compressed or not - defaults to true.
     */
    @Deprecated
    protected boolean messageCompressionEnabled = true;

    /**
     * Default, easy constructor.
     * <p>
     * A client instance instantiated through this constructor will use the Sentry DSN returned by
     * {@link SentryDsn#buildOptional()} and perform an automatic start.
     * </p>
     */
    @Deprecated
    public Client() {
        raven = new Raven();
    }

    /**
     * Extension of the easy constructor {@link #Client()} that allows you to turn off the autostart behavior.
     *
     * @param autoStart whether to start the underlying transport automatically or not
     */
    @Deprecated
    public Client(boolean autoStart) {
        raven = new Raven();
    }

    /**
     * Constructor that performs an autostart using the transport determined by the supplied dsn.
     * <p>
     * Watch out: this constructor will always use the supplied dsn and not look for a Sentry DSN in other locations
     * such as an environment variable or system property.
     * </p>
     *
     * @param dsn dsn to use
     */
    @Deprecated
    public Client(SentryDsn dsn) {
        raven = new Raven(dsn.toString(true));
    }

    /**
     * Constructor using the transport determined by the supplied dsn.
     * <p>
     * Watch out: this constructor will always use the supplied dsn and not look for a Sentry DSN in other locations
     * such as an environment variable or system property.
     * </p>
     *
     * @param dsn       dsn to use
     * @param autoStart whether to start the underlying transport layer automatically
     */
    @Deprecated
    public Client(SentryDsn dsn, boolean autoStart) {
        raven = new Raven(dsn.toString(true));
    }

    /**
     * Set the processors to be used by this client. Instances from the list are
     * copied over.
     *
     * @param processors a list of processors to be used by this client
     */
    @Deprecated
    public synchronized void setJSONProcessors(List processors) {
        //NOOP
    }

    @Deprecated
    public boolean isMessageCompressionEnabled() {
        //NOOP
        return false;
    }

    @Deprecated
    public void setMessageCompressionEnabled(boolean messageCompressionEnabled) {
        //NOOP
    }

    private String buildAndSendEvent(EventBuilder eventBuilder) {
        for (EventBuilderHelper builderHelper : raven.getBuilderHelpers()) {
            builderHelper.helpBuildingEvent(eventBuilder);
        }
        Event event = eventBuilder.build();
        raven.sendEvent(event);
        return event.getId().toString().replaceAll("-", "");

    }

    private static Event.Level convertLevel(Integer logLevel) {
        if (logLevel == null)
            return null;

        switch (logLevel) {
            case 1:
                return Event.Level.DEBUG;
            case 2:
                return Event.Level.INFO;
            case 3:
                return Event.Level.WARNING;
            case 4:
                return Event.Level.ERROR;
            case 5:
                return Event.Level.FATAL;
            default:
                return null;
        }
    }


    @Deprecated
    public String captureMessage(String msg) {
        EventBuilder eventBuilder = new EventBuilder().setMessage(msg);
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureMessage(String msg, Map<String, ?> tags) {
        EventBuilder eventBuilder = new EventBuilder().setMessage(msg);
        for (Map.Entry<String, ?> tag : tags.entrySet()) {
            eventBuilder.addTag(tag.getKey(), tag.getValue().toString());
        }
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureMessage(String message, Long timestamp, String loggerClass, Integer logLevel, String culprit) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(message)
                .setTimestamp(new Date(timestamp))
                .setLogger(loggerClass)
                .setLevel(convertLevel(logLevel))
                .setCulprit(culprit);

        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureMessage(String message, Long timestamp, String loggerClass, Integer logLevel, String culprit, Map<String, ?> tags) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(message)
                .setTimestamp(new Date(timestamp))
                .setLogger(loggerClass)
                .setLevel(convertLevel(logLevel))
                .setCulprit(culprit);
        for (Map.Entry<String, ?> tag : tags.entrySet()) {
            eventBuilder.addTag(tag.getKey(), tag.getValue().toString());
        }

        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureException(Throwable exception) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(exception.getMessage())
                .addSentryInterface(new ExceptionInterface(exception));
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureException(Throwable exception, Map<String, ?> tags) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(exception.getMessage())
                .addSentryInterface(new ExceptionInterface(exception));
        for (Map.Entry<String, ?> tag : tags.entrySet()) {
            eventBuilder.addTag(tag.getKey(), tag.getValue().toString());
        }
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureException(String logMessage, long timestamp, String loggerName, Integer logLevel, String culprit, Throwable exception) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(logMessage)
                .setTimestamp(new Date(timestamp))
                .setLogger(loggerName)
                .setLevel(convertLevel(logLevel))
                .setCulprit(culprit)
                .addSentryInterface(new ExceptionInterface(exception));
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public String captureException(String logMessage, long timestamp, String loggerName, Integer logLevel, String culprit, Throwable exception, Map<String, ?> tags) {
        EventBuilder eventBuilder = new EventBuilder()
                .setMessage(logMessage)
                .setTimestamp(new Date(timestamp))
                .setLogger(loggerName)
                .setLevel(convertLevel(logLevel))
                .setCulprit(culprit)
                .addSentryInterface(new ExceptionInterface(exception));
        for (Map.Entry<String, ?> tag : tags.entrySet()) {
            eventBuilder.addTag(tag.getKey(), tag.getValue().toString());
        }
        return buildAndSendEvent(eventBuilder);
    }

    @Deprecated
    public synchronized void start() {
        //NOOP
    }

    @Deprecated
    public boolean isStarted() {
        return true;
    }

    @Deprecated
    public synchronized void stop() {
        try {
            raven.getConnection().close();
        } catch (IOException e) {
            //TODO: Handle this properly
            e.printStackTrace();
        }
    }

    @Deprecated
    public boolean isDisabled() {
        return false;
    }

    @Deprecated
    protected Message buildMessage(String message, String timestamp, String loggerClass, Integer logLevel, String culprit, Throwable exception, Map<String, ?> tags) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    protected void send(Message message, long timestamp) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generates a unique event id.
     *
     * @return hexadecimal UUID4 String
     */
    @Deprecated
    protected String generateEventId() {
        // If we keep the -'s in the uuid, it is too long, remove them
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * Formats a timestamp in the format expected by Sentry.
     *
     * @param timestamp timestamp to format
     * @return formatted timestamp
     */
    @Deprecated
    protected String formatTimestamp(long timestamp) {
        return DateFormatUtils.formatUTC(timestamp, DateFormatUtils.ISO_DATETIME_FORMAT.getPattern());
    }

    /**
     * Registers the transport class for the given scheme.
     *
     * @param scheme         scheme to register for
     * @param transportClass transport class to register for the scheme
     * @return the previously registered transport class for the scheme, if any
     */
    @Deprecated
    public static Class register(String scheme, Class transportClass) {
        return TRANSPORT_REGISTRY.put(scheme, transportClass);
    }

    /**
     * Registers the default transport classes.
     */
    @Deprecated
    public static void registerDefaults() {
        //NOOP
    }

    /**
     * Builds the HMAC sentry signature.
     * <p/>
     * The header is composed of a SHA1-signed HMAC, the timestamp from when the message was generated,
     * and an arbitrary client version string.
     * <p/>
     * The client version should be something distinct to your client, and is simply for reporting purposes.
     * To generate the HMAC signature, take the following example (in Python):
     * <p/>
     * hmac.new(public_key, '%s %s' % (timestamp, message), hashlib.sha1).hexdigest()
     *
     * @param message   the error message to send to sentry
     * @param timestamp the timestamp for when the message was created
     * @param key       sentry public key
     * @return SHA1-signed HMAC string
     */
    @Deprecated
    public static String sign(String message, long timestamp, String key) {
        final String algo = "HmacSHA1";
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), algo);
            Mac mac = Mac.getInstance(algo);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal((timestamp + " " + message).getBytes());
            return new String(Hex.encodeHex(rawHmac));
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidConfig("Could not sign message: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new InvalidConfig("Could not sign message: " + e.getMessage(), e);
        }
    }

    /**
     * An almost-unique hash identifying the this event to improve aggregation.
     *
     * @param message The message we are sending to sentry
     * @return CRC32 Checksum string
     */
    @Deprecated
    public static String calculateChecksum(String message) {
        byte bytes[] = message.getBytes();
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return String.valueOf(checksum.getValue());
    }

    @Deprecated
    public static class InvalidConfig extends RuntimeException {

        public InvalidConfig(String msg) {
            super(msg);
        }

        public InvalidConfig(String msg, Throwable t) {
            super(msg, t);
        }

    }

    @Deprecated
    public static class Message {

        @Deprecated
        public static final Message NONE = new Message(null, "-1", false);

        @Deprecated
        public final JSONObject json;
        @Deprecated
        public final String eventId;
        @Deprecated
        public final boolean compress;

        @Deprecated
        public Message(JSONObject json, String eventId, boolean compress) {
            this.json = json;
            this.eventId = eventId;
            this.compress = compress;
        }

        @Deprecated
        public String encoded() {
            byte[] raw = Utils.toUtf8(json.toJSONString());
            if (compress) {
                raw = Utils.compress(raw);
            }
            return encodeBase64String(raw);
        }

        @Override
        @Deprecated
        public String toString() {
            return json.toJSONString();
        }
    }

}

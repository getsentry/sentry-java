package net.kencochrane.raven;

import net.kencochrane.raven.spi.JSONProcessor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.time.DateFormatUtils;
import org.json.simple.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ConnectException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * <li>http: {@link Transport.Http}</li>
 * <li>https: {@link Transport.Http}</li>
 * <li>naive+https: {@link Transport.NaiveHttps}</li>
 * <li>udp: {@link Transport.Udp}</li>
 * </ul>
 * <p>
 * Any of these schemes can be prefixed with <code>async+</code> in which case the transport built using the original
 * scheme will be wrapped in the transport class registered for {@link #VARIANT_ASYNC}, which is {@link AsyncTransport}
 * by default.
 * </p>
 */
public class Client {

    public interface Default {
        String LOGGER = "root";
        int LOG_LEVEL = Events.LogLevel.ERROR.intValue;
        String EMPTY_MESSAGE = "(empty)";
    }

    /**
     * Async transport layers require some extra work when instantiating.
     */
    public static final String VARIANT_ASYNC = "async";

    /**
     * The registry mapping schemes to transport layer classes.
     */
    protected static final Map<String, Class<? extends Transport>> TRANSPORT_REGISTRY = new HashMap<String, Class<? extends Transport>>();

    /**
     * Logger.
     */
    private static final Logger LOG = Logger.getLogger("raven.client");

    /**
     * The dsn used by this client.
     */
    public final SentryDsn dsn;

    /**
     * The transport layer used by this client.
     */
    protected Transport transport;

    /**
     * Whether messages should be compressed or not - defaults to true.
     */
    protected boolean messageCompressionEnabled = true;

    /**
     * JSONProcessor instances. Initialized with an empty list to prevent NPE.
     */
    private List<JSONProcessor> jsonProcessors = Collections.emptyList();

    static {
        registerDefaults();
    }

    /**
     * Default, easy constructor.
     * <p>
     * A client instance instantiated through this constructor will use the Sentry DSN returned by
     * {@link SentryDsn#buildOptional()} and perform an automatic start.
     * </p>
     */
    public Client() {
        this(true);
    }

    /**
     * Extension of the easy constructor {@link #Client()} that allows you to turn off the autostart behavior.
     *
     * @param autoStart whether to start the underlying transport automatically or not
     */
    public Client(boolean autoStart) {
        this(SentryDsn.buildOptional(), autoStart);
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
    public Client(SentryDsn dsn) {
        this(dsn, true);
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
    public Client(SentryDsn dsn, boolean autoStart) {
        this.dsn = dsn;
        if (autoStart) {
            start();
        }
    }

    /**
     * Construct a client using the given transport.
     *
     * @param transport transport to use
     */
    public Client(Transport transport) {
        this(transport, true);
    }

    /**
     * Construct a client using the given transport.
     *
     * @param transport transport to use
     * @param autoStart whether to start the transport automatically
     */
    public Client(Transport transport, boolean autoStart) {
        this.dsn = transport.dsn;
        this.transport = transport;
        if (autoStart) {
            start();
        }
    }

    /**
     * Set the processors to be used by this client. Instances from the list are
     * copied over.
     *
     * @param processors a list of processors to be used by this client
     */
    public synchronized void setJSONProcessors(List<JSONProcessor> processors) {
        this.jsonProcessors = new ArrayList<JSONProcessor>(processors.size());
        this.jsonProcessors.addAll(processors);
    }

    public boolean isMessageCompressionEnabled() {
        return messageCompressionEnabled;
    }

    public void setMessageCompressionEnabled(boolean messageCompressionEnabled) {
        this.messageCompressionEnabled = messageCompressionEnabled;
    }

    public String captureMessage(String msg) {
        return captureMessage(msg, null, null, null, null);
    }

    public String captureMessage(String msg, Map<String, ?> tags) {
        return captureMessage(msg, null, null, null, null, tags);
    }

    public String captureMessage(String message, Long timestamp, String loggerClass, Integer logLevel, String culprit) {
        return captureMessage(message, timestamp, loggerClass, logLevel, culprit, null);
    }

    public String captureMessage(String message, Long timestamp, String loggerClass, Integer logLevel, String culprit, Map<String, ?> tags) {
        timestamp = (timestamp == null ? Utils.now() : timestamp);
        Message msg = buildMessage(message, formatTimestamp(timestamp), loggerClass, logLevel, culprit, null, tags);
        send(msg, timestamp);
        return msg.eventId;
    }

    public String captureException(Throwable exception) {
        long timestamp = Utils.now();
        return captureException(exception.getMessage(), timestamp, null, null, null, exception);
    }

    public String captureException(Throwable exception, Map<String, ?> tags) {
        long timestamp = Utils.now();
        return captureException(exception.getMessage(), timestamp, null, null, null, exception, tags);
    }

    public String captureException(String logMessage, long timestamp, String loggerName, Integer logLevel, String culprit, Throwable exception) {
        return captureException(logMessage, timestamp, loggerName, logLevel, culprit, exception, null);
    }

    public String captureException(String logMessage, long timestamp, String loggerName, Integer logLevel, String culprit, Throwable exception, Map<String, ?> tags) {
        Message message = buildMessage(logMessage, formatTimestamp(timestamp), loggerName, logLevel, culprit, exception, tags);
        send(message, timestamp);
        return message.eventId;
    }

    public synchronized void start() {
        if (isDisabled()) {
            return;
        }
        if (transport == null) {
            transport = newTransport(dsn);
        }
        transport.start();
    }

    public boolean isStarted() {
        return transport != null && transport.isStarted();
    }

    public synchronized void stop() {
        if (transport == null) {
            return;
        }
        transport.stop();
        transport = null;
    }

    public boolean isDisabled() {
        return dsn == null;
    }

    @SuppressWarnings("unchecked")
    protected Message buildMessage(String message, String timestamp, String loggerClass, Integer logLevel, String culprit, Throwable exception, Map<String, ?> tags) {
        if (isDisabled()) {
            return Message.NONE;
        }
        String eventId = generateEventId();
        JSONObject obj = new JSONObject();
        if (exception == null) {
            obj.put("culprit", culprit);
            Events.message(obj, message);
        } else {
            Events.exception(obj, exception);
        }
        if (message == null) {
            message = (exception == null ? null : exception.getMessage());
            message = (message == null ? Default.EMPTY_MESSAGE : message);
        }
        obj.put("event_id", eventId);
        obj.put("checksum", calculateChecksum(message));
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", dsn.projectId);
        obj.put("level", logLevel == null ? Default.LOG_LEVEL : logLevel);
        obj.put("logger", loggerClass == null ? Default.LOGGER : loggerClass);
        obj.put("server_name", Utils.hostname());
        if (tags != null) {
            JSONObject jsonTags = new JSONObject();
            jsonTags.putAll(tags);
            obj.put("tags", jsonTags);
        }

        for (JSONProcessor processor : jsonProcessors) {
            processor.process(obj, exception);
        }
        return new Message(obj, eventId, messageCompressionEnabled);
    }

    protected void send(Message message, long timestamp) {
        if (isDisabled()) {
            return;
        }
        try {
            transport.send(message.encoded(), timestamp);
        } catch (ConnectException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
            // TODO Add backing off in case of errors
        } catch (FileNotFoundException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
            // TODO Add backing off in case of errors
        } catch (IOException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Generates a unique event id.
     *
     * @return hexadecimal UUID4 String
     */
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
    protected String formatTimestamp(long timestamp) {
        return DateFormatUtils.formatUTC(timestamp, DateFormatUtils.ISO_DATETIME_FORMAT.getPattern());
    }

    /**
     * Builds a new transport instance, using a transport class, registered in the {@link #TRANSPORT_REGISTRY}
     * through the {@link #register(String, Class)} method, matching the scheme of the dsn.
     *
     * @param dsn dsn
     * @return transport layer for the given dsn
     */
    public static Transport newTransport(SentryDsn dsn) {
        String fullScheme = dsn.getFullScheme(VARIANT_ASYNC);
        Class<? extends Transport> transportClass = TRANSPORT_REGISTRY.get(fullScheme);
        if (transportClass == null) {
            throw new InvalidConfig("No transport registered for " + fullScheme);
        }
        try {
            Constructor<? extends Transport> constructor = transportClass.getConstructor(SentryDsn.class);
            Transport transport = constructor.newInstance(dsn);
            if (dsn.isVariantIncluded(VARIANT_ASYNC)) {
                return newAsyncTransport(transport);
            }
            return transport;
        } catch (NoSuchMethodException e) {
            throw new InvalidConfig("A transport class should contain a constructor with a SentryDsn instance as parameter", e);
        } catch (InvocationTargetException e) {
            throw new InvalidConfig("Could not construct a transport layer for " + fullScheme, e);
        } catch (InstantiationException e) {
            throw new InvalidConfig("Could not construct a transport layer for " + fullScheme, e);
        } catch (IllegalAccessException e) {
            throw new InvalidConfig("Could not construct a transport layer for " + fullScheme, e);
        }
    }

    /**
     * Builds a new async transport layer, wrapping the original transport.
     *
     * @param transport transport to wrap in an async layer
     * @return the async transport wrapper
     */
    public static Transport newAsyncTransport(Transport transport) {
        Class<? extends Transport> transportClass = TRANSPORT_REGISTRY.get(VARIANT_ASYNC);
        if (transportClass == null) {
            throw new InvalidConfig("No async transport registered");
        }
        final String invalidSignature = "The async transport handler should contain a public static \"build\" method " + //
                "with a single parameter of type " + Transport.class + " and returning a new " + Transport.class + " instance.";
        try {
            Method method = transportClass.getMethod("build", Transport.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new InvalidConfig(invalidSignature);
            }
            Object result = method.invoke(null, transport);
            if (!(result instanceof Transport)) {
                throw new InvalidConfig("The build method of the async transport layer should return an instance of " + Transport.class);
            }
            return (Transport) result;
        } catch (InvocationTargetException e) {
            throw new InvalidConfig("Could not invoke the static build method of " + transportClass.getName(), e);
        } catch (NoSuchMethodException e) {
            throw new InvalidConfig(invalidSignature, e);
        } catch (IllegalAccessException e) {
            throw new InvalidConfig(invalidSignature, e);
        }
    }

    /**
     * Registers the transport class for the given scheme.
     *
     * @param scheme         scheme to register for
     * @param transportClass transport class to register for the scheme
     * @return the previously registered transport class for the scheme, if any
     */
    public static Class<? extends Transport> register(String scheme, Class<? extends Transport> transportClass) {
        return TRANSPORT_REGISTRY.put(scheme, transportClass);
    }

    /**
     * Registers the default transport classes.
     */
    public static void registerDefaults() {
        TRANSPORT_REGISTRY.put("http", Transport.Http.class);
        TRANSPORT_REGISTRY.put("https", Transport.Http.class);
        TRANSPORT_REGISTRY.put("naive+https", Transport.NaiveHttps.class);
        TRANSPORT_REGISTRY.put("udp", Transport.Udp.class);
        TRANSPORT_REGISTRY.put(VARIANT_ASYNC, AsyncTransport.class);
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
    public static String calculateChecksum(String message) {
        byte bytes[] = message.getBytes();
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return String.valueOf(checksum.getValue());
    }

    public static class InvalidConfig extends RuntimeException {

        public InvalidConfig(String msg) {
            super(msg);
        }

        public InvalidConfig(String msg, Throwable t) {
            super(msg, t);
        }

    }

    public static class Message {

        public static final Message NONE = new Message(null, "-1", false);

        public final JSONObject json;
        public final String eventId;
        public final boolean compress;

        public Message(JSONObject json, String eventId, boolean compress) {
            this.json = json;
            this.eventId = eventId;
            this.compress = compress;
        }

        public String encoded() {
            byte[] raw = Utils.toUtf8(json.toJSONString());
            if (compress) {
                raw = Utils.compress(raw);
            }
            return encodeBase64String(raw);
        }

        @Override
        public String toString() {
            return json.toJSONString();
        }
    }

}

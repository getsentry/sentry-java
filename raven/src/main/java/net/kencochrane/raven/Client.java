package net.kencochrane.raven;

import net.kencochrane.sentry.RavenUtils;
import org.json.simple.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.kencochrane.sentry.RavenUtils.getTimestampString;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * Raven client for Java, allowing sending of messages to Sentry.
 * <p/>
 */
public class Client {

    public static final String VARIANT_ASYNC = "async";
    private static final Logger LOG = Logger.getLogger("raven.client");
    public final SentryDsn dsn;
    protected Transport transport;
    protected static final Map<String, Class<? extends Transport>> TRANSPORT_REGISTRY = new HashMap<String, Class<? extends Transport>>();

    static {
        TRANSPORT_REGISTRY.put("http", Transport.Http.class);
        TRANSPORT_REGISTRY.put("https", Transport.Http.class);
        TRANSPORT_REGISTRY.put("naive+https", Transport.NaiveHttps.class);
        TRANSPORT_REGISTRY.put("udp", Transport.Udp.class);
        TRANSPORT_REGISTRY.put(VARIANT_ASYNC, AsyncTransport.class);
    }

    public Client() {
        this(true);
    }

    public Client(boolean autoStart) {
        this(SentryDsn.build(), autoStart);
    }

    public Client(SentryDsn dsn) {
        this(dsn, true);
    }

    public Client(SentryDsn dsn, boolean autoStart) {
        this.dsn = dsn;
        if (autoStart) {
            start();
        }
    }

    public Client(Transport transport) {
        this(transport, true);
    }

    public Client(Transport transport, boolean autoStart) {
        this.dsn = transport.dsn;
        this.transport = transport;
        if (autoStart) {
            start();
        }
    }

    public String captureMessage(String msg) {
        return captureMessage(msg, null, null, null, null);
    }

    public String captureMessage(String message, Long timestamp, String loggerClass, Integer logLevel, String culprit) {
        timestamp = (timestamp == null ? RavenUtils.getTimestampLong() : timestamp);
        Message msg = buildMessage(message, RavenUtils.getTimestampString(timestamp), loggerClass, logLevel, culprit, null);
        send(msg, timestamp);
        return msg.eventId;
    }

    public String captureException(Throwable exception) {
        long timestamp = RavenUtils.getTimestampLong();
        return captureException(exception.getMessage(), timestamp, null, null, null, exception);
    }

    public String captureException(String logMessage, long timestamp, String loggerName, Integer logLevel, String culprit, Throwable exception) {
        Message message = buildMessage(logMessage, getTimestampString(timestamp), loggerName, logLevel, culprit, exception);
        send(message, timestamp);
        return message.eventId;
    }

    public void start() {
        if (transport == null) {
            transport = newTransport(dsn);
        }
        transport.start();
    }

    public boolean isStarted() {
        return transport != null && transport.isStarted();
    }

    public void stop() {
        if (transport == null) {
            return;
        }
        transport.stop();
        transport = null;
    }

    @SuppressWarnings("unchecked")
    protected Message buildMessage(String message, String timestamp, String loggerClass, Integer logLevel, String culprit, Throwable exception) {
        String eventId = RavenUtils.getRandomUUID();
        JSONObject obj = new JSONObject();
        if (exception == null) {
            obj.put("culprit", culprit);
        } else {
            JSONObject exceptionJson = Events.exception(exception);
            obj.putAll(exceptionJson);
        }
        if (message == null) {
            message = (exception == null ? null : exception.getMessage());
            message = "(empty)";
        }
        obj.put("event_id", eventId);
        obj.put("checksum", RavenUtils.calculateChecksum(message));
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", dsn.projectId);
        obj.put("level", logLevel == null ? Events.LogLevel.ERROR.intValue : logLevel);
        obj.put("logger", loggerClass == null ? "root" : loggerClass);
        obj.put("server_name", RavenUtils.getHostname());
        return new Message(obj, eventId);
    }

    protected void send(Message message, long timestamp) {
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

    public static Transport newAsyncTransport(Transport transport) {
        Class<? extends Transport> transportClass = TRANSPORT_REGISTRY.get(VARIANT_ASYNC);
        if (transportClass == null) {
            throw new InvalidConfig("No async transport registered");
        }
        try {
            Method method = transportClass.getMethod("build", Transport.class);
            Object result = method.invoke(null, transport);
            if (!(result instanceof Transport)) {
                throw new InvalidConfig("The build method of the async transport layer should return an instance of " + Transport.class);
            }
            return (Transport) result;
        } catch (InvocationTargetException e) {
            throw new InvalidConfig("Could not invoke the static build method of " + transportClass.getName(), e);
        } catch (NoSuchMethodException e) {
            String msg = "The async transport handler should contain a publci static \"build\" method with a single " + //
                    "parameter of type " + Transport.class + " and returning a new " + Transport.class + " instance.";
            throw new InvalidConfig(msg, e);
        } catch (IllegalAccessException e) {
            String msg = "The async transport handler should contain a publci static \"build\" method with a single " + //
                    "parameter of type " + Transport.class + " and returning a new " + Transport.class + " instance.";
            throw new InvalidConfig(msg, e);
        }
    }

    public static Class<? extends Transport> register(String scheme, Class<? extends Transport> transportClass) {
        return TRANSPORT_REGISTRY.put(scheme, transportClass);
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

        public final JSONObject json;
        public final String eventId;

        public Message(JSONObject json, String eventId) {
            this.json = json;
            this.eventId = eventId;
        }

        public String encoded() {
            return encodeBase64String(RavenUtils.toUtf8(json.toJSONString()));
        }

        @Override
        public String toString() {
            return json.toJSONString();
        }
    }

}

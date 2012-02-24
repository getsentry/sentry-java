package net.kencochrane.sentry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 11:59 AM
 */

class RavenClient {

    private static final String RAVEN_JAVA_VERSION = "Raven-Java 0.4";
    private RavenConfig config;
    private String sentryDSN;
    private String lastID;

    public RavenClient() {
        this.sentryDSN = System.getenv("SENTRY_DSN");
        if (this.sentryDSN == null || this.sentryDSN.length() == 0) {
            throw new RuntimeException("You must provide a DSN to RavenClient");
        }
        this.config = new RavenConfig(this.sentryDSN);
    }

    public RavenClient(String sentryDSN) {
        this.sentryDSN = sentryDSN;
        this.config = new RavenConfig(sentryDSN);
    }

    public RavenClient(String sentryDSN, String proxy) {
        this.sentryDSN = sentryDSN;
        this.config = new RavenConfig(sentryDSN, proxy);
    }

    public RavenConfig getConfig() {
        return config;
    }

    public void setConfig(RavenConfig config) {
        this.config = config;
    }

    public String getSentryDSN() {
        return sentryDSN;
    }

    public void setSentryDSN(String sentryDSN) {
        this.sentryDSN = sentryDSN;
    }

    public void setLastID(String lastID) {
        this.lastID = lastID;
    }

    public String getLastID() {
        return lastID;
    }

    /**
     * Build up the JSON body for the POST that is sent to sentry
     *
     * @param message     The log message
     * @param timestamp   ISO8601 formatted date string
     * @param loggerClass The class associated with the log message
     * @param logLevel    int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit     Who we think caused the problem.
     * @return JSON String of message body
     */
    private String buildJSON(String message, String timestamp, String loggerClass, int logLevel, String culprit, Throwable exception) {
        JSONObject obj = new JSONObject();
        String lastID = RavenUtils.getRandomUUID();
        obj.put("event_id", lastID); //Hexadecimal string representing a uuid4 value.
        obj.put("checksum", RavenUtils.calculateChecksum(message));
        if (exception == null) {
            obj.put("culprit", culprit);
        } else {
            obj.put("culprit", determineCulprit(exception));
            obj.put("sentry.interfaces.Exception", buildException(exception));
            obj.put("sentry.interfaces.Stacktrace", buildStacktrace(exception));
        }
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", getConfig().getProjectId());
        obj.put("level", logLevel);
        obj.put("logger", loggerClass);
        obj.put("server_name", RavenUtils.getHostname());
        setLastID(lastID);
        return obj.toJSONString();
    }

    /**
     * Determines the class and method name where the root cause exception occurred.
     *
     * @param exception exception
     * @return the culprit
     */
    private String determineCulprit(Throwable exception) {
        Throwable cause = exception;
        String culprit = null;
        while (cause != null) {
            StackTraceElement[] elements = cause.getStackTrace();
            if (elements.length > 0) {
                StackTraceElement trace = elements[0];
                culprit = trace.getClassName() + "." + trace.getMethodName();
            }
            cause = cause.getCause();
        }
        return culprit;
    }

    private JSONObject buildException(Throwable exception) {
        JSONObject json = new JSONObject();
        json.put("type", exception.getClass().getSimpleName());
        json.put("value", exception.getMessage());
        json.put("module", exception.getClass().getPackage().getName());
        return json;
    }

    private JSONObject buildStacktrace(Throwable exception) {
        JSONArray array = new JSONArray();
        Throwable cause = exception;
        while (cause != null) {
            StackTraceElement[] elements = cause.getStackTrace();
            for (int index = 0; index < elements.length; ++index) {
                if (index == 0) {
                    JSONObject causedByFrame = new JSONObject();
                    String msg = "Caused by: " + cause.getClass().getName();
                    if (cause.getMessage() != null) {
                        msg += " (\"" + cause.getMessage() + "\")";
                    }
                    causedByFrame.put("filename", msg);
                    causedByFrame.put("lineno", -1);
                    array.add(causedByFrame);
                }
                StackTraceElement element = elements[index];
                JSONObject frame = new JSONObject();
                frame.put("filename", element.getClassName());
                frame.put("function", element.getMethodName());
                frame.put("lineno", element.getLineNumber());
                array.add(frame);
            }
            cause = cause.getCause();
        }
        JSONObject stacktrace = new JSONObject();
        stacktrace.put("frames", array);
        return stacktrace;
    }

    /**
     * Take the raw message body and get it ready for sending. Encode and compress it.
     *
     * @param jsonMessage the message we want to prepare
     * @return Encode and compressed version of the jsonMessage
     */
    private String buildMessageBody(String jsonMessage) {
        //need to zip and then base64 encode the message.
        // compressing doesn't work right now, sentry isn't decompressing correctly.
        // come back to it later.
        //return compressAndEncode(jsonMessage);

        // in the meantime just base64 encode it.
        return encodeBase64String(jsonMessage.getBytes());

    }

    /**
     * Build up the JSON body and then Encode and compress it.
     *
     * @param message     The log message
     * @param timestamp   ISO8601 formatted date string
     * @param loggerClass The class associated with the log message
     * @param logLevel    int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit     Who we think caused the problem.
     * @param exception   exception causing the problem
     * @return Encode and compressed version of the JSON Message body
     */
    private String buildMessage(String message, String timestamp, String loggerClass, int logLevel, String culprit, Throwable exception) {
        // get the json version of the body
        String jsonMessage = buildJSON(message, timestamp, loggerClass, logLevel, culprit, exception);

        // compress and encode the json message.
        return buildMessageBody(jsonMessage);
    }


    /**
     * build up the sentry auth header in the following format
     * <p/>
     * The header is composed of a SHA1-signed HMAC, the timestamp from when the message was generated, and an
     * arbitrary client version string. The client version should be something distinct to your client,
     * and is simply for reporting purposes.
     * <p/>
     * X-Sentry-Auth: Sentry sentry_version=2.0,
     * sentry_signature=<hmac signature>,
     * sentry_timestamp=<signature timestamp>[,
     * sentry_key=<public api key>,[
     * sentry_client=<client version, arbitrary>]]
     *
     * @param hmacSignature SHA1-signed HMAC
     * @param timestamp     is the timestamp of which this message was generated
     * @param publicKey     is either the public_key or the shared global key between client and server.
     * @return String version of the sentry auth header
     */
    private String buildAuthHeader(String hmacSignature, long timestamp, String publicKey) {
        StringBuilder header = new StringBuilder();
        header.append("Sentry sentry_version=2.0,sentry_signature=");
        header.append(hmacSignature);
        header.append(",sentry_timestamp=");
        header.append(timestamp);
        header.append(",sentry_key=");
        header.append(publicKey);
        header.append(",sentry_client=");
        header.append(RAVEN_JAVA_VERSION);

        return header.toString();
    }

    /**
     * Send the message to the sentry server.
     *
     * @param messageBody the encoded json message we are sending to the sentry server
     * @param timestamp   the timestamp of the message
     */
    private void sendMessage(String messageBody, long timestamp) {
        HttpURLConnection connection = null;
        try {

            // get the hmac Signature for the header
            String hmacSignature = RavenUtils.getSignature(messageBody, timestamp, config.getSecretKey());

            // get the auth header
            String authHeader = buildAuthHeader(hmacSignature, timestamp, getConfig().getPublicKey());

            URL endpoint = new URL(getConfig().getSentryURL());
            connection = (HttpURLConnection) endpoint.openConnection(getConfig().getProxy());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("X-Sentry-Auth", authHeader);
            OutputStream output = connection.getOutputStream();
            output.write(messageBody.getBytes());
            output.close();
            connection.connect();
            InputStream input = connection.getInputStream();
            input.close();
        } catch (IOException e) {
            // Eat the errors, we don't want to cause problems if there are major issues.
            e.printStackTrace();
        }
    }

    /**
     * Send the log message to the sentry server.
     *
     * This method is deprecated. You should use captureMessage or captureException instead.
     *
     * @deprecated
     * @param theLogMessage The log message
     * @param timestamp     unix timestamp
     * @param loggerClass   The class associated with the log message
     * @param logLevel      int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit       Who we think caused the problem.
     * @param exception     exception that occurred
     */
    public void logMessage(String theLogMessage, long timestamp, String loggerClass, int logLevel, String culprit, Throwable exception) {
        String message = buildMessage(theLogMessage, RavenUtils.getTimestampString(timestamp), loggerClass, logLevel, culprit, exception);
        sendMessage(message, timestamp);
    }


    /**
     * Send the log message to the sentry server.
     *
     * @param message       The log message
     * @param timestamp     unix timestamp
     * @param loggerClass   The class associated with the log message
     * @param logLevel      int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit       Who we think caused the problem.
     * @return lastID       The ID for the last message.
     */
    public String captureMessage(String message, long timestamp, String loggerClass, int logLevel, String culprit) {
        String body = buildMessage(message, RavenUtils.getTimestampString(timestamp), loggerClass, logLevel, culprit, null);
        sendMessage(body, timestamp);
        return getLastID();
    }

    /**
     * Send the log message to the sentry server.
     *
     * @param message       The log message
     * @return lastID       The ID for the last message.
     */
    public String captureMessage(String message) {
        return captureMessage(message, RavenUtils.getTimestampLong(), "root", 50, null);
    }

    /**
     * Send the exception to the sentry server.
     *
     * @param message       The log message
     * @param timestamp     unix timestamp
     * @param loggerClass   The class associated with the log message
     * @param logLevel      int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit       Who we think caused the problem.
     * @param exception     exception that occurred
     * @return lastID       The ID for the last message.
     */
    public String captureException(String message, long timestamp, String loggerClass, int logLevel, String culprit, Throwable exception) {
        String body = buildMessage(message, RavenUtils.getTimestampString(timestamp), loggerClass, logLevel, culprit, exception);
        sendMessage(body, timestamp);
        return getLastID();
    }

    /**
     * Send an exception to the sentry server.
     *
     * @param exception     exception that occurred
     * @return lastID       The ID for the last message. 
     */
    public String captureException(Throwable exception) {
        return captureException(exception.getMessage(), RavenUtils.getTimestampLong(), "root", 50, null, exception);
    }
}
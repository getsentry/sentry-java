package net.kencochrane.sentry;

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

    private static final String RAVEN_JAVA_VERSION = "Raven-Java 0.2";
    private RavenConfig config;
    private String sentryDSN;

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
    private String buildJSON(String message, String timestamp, String loggerClass, int logLevel, String culprit) {
        JSONObject obj = new JSONObject();
        obj.put("event_id", RavenUtils.getRandomUUID()); //Hexadecimal string representing a uuid4 value.
        obj.put("checksum", RavenUtils.calculateChecksum(message));
        obj.put("culprit", culprit);
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", getConfig().getProjectId());
        obj.put("level", logLevel);
        obj.put("logger", loggerClass);
        obj.put("server_name", RavenUtils.getHostname());
        return obj.toJSONString();
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
     * @return Encode and compressed version of the JSON Message body
     */
    private String buildMessage(String message, String timestamp, String loggerClass, int logLevel, String culprit) {
        // get the json version of the body
        String jsonMessage = buildJSON(message, timestamp, loggerClass, logLevel, culprit);

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
     * @param theLogMessage The log message
     * @param timestamp     unix timestamp
     * @param loggerClass   The class associated with the log message
     * @param logLevel      int value for Log level for message (DEBUG, ERROR, INFO, etc.)
     * @param culprit       Who we think caused the problem.
     */
    public void logMessage(String theLogMessage, long timestamp, String loggerClass, int logLevel, String culprit) {
        String timestampDate = RavenUtils.getTimestampString(timestamp);

        String message = buildMessage(theLogMessage, timestampDate, loggerClass, logLevel, culprit);
        sendMessage(message, timestamp);
    }
}
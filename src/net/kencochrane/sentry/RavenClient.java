package net.kencochrane.sentry;

import org.apache.commons.codec.binary.Hex;
import org.json.simple.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SignatureException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.GZIPOutputStream;


import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * User: kcochrane
 * Date: 2/6/12
 * Time: 11:59 AM
 */


public class RavenClient {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    public static SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /**
     * Computes RFC 2104-compliant HMAC signature.
     * Based off of the sample here. http://docs.amazonwebservices.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/AuthJavaSampleHMACSignature.html
     *
     * @param data The data to be signed.
     * @param key  The signing key.
     * @return The hex-encoded RFC 2104-compliant HMAC signature.
     * @throws java.security.SignatureException
     *          when signature generation fails
     */
    public static String calculateHMAC(String data, String key) throws java.security.SignatureException {
        String result;
        try {

            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);

            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);

            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes());

            result = hexEncode(rawHmac);

        } catch (Exception e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }

    public String getHostname() {
        String hostname = null;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            // can't get hostname
            hostname = "unavailable";
        }
        return hostname;
    }

    public String getDateAsISO8601String(Date date) {
        String result = ISO8601FORMAT.format(date);
        //convert YYYYMMDDTHH:mm:ss+HH00 into YYYYMMDDTHH:mm:ss+HH:00
        //- note the added colon for the Timezone
        result = result.substring(0, result.length() - 2)
                + ":" + result.substring(result.length() - 2);
        return result;
    }

    public long getTimestampLong() {
        java.util.Date date = new java.util.Date();
        return date.getTime();
    }

    public String getTimestampString(long timestamp) {

        java.util.Date date = new java.util.Date(timestamp);
        return getDateAsISO8601String(date);
    }

    public String getTimestampString() {

        java.util.Date date = new java.util.Date();
        return getDateAsISO8601String(date);
    }

    public String getSignature(String message, long timestamp, String key) {
        //System.out.println("message = '" + message + "'");
        //System.out.println("timestamp = " + timestamp);
       // System.out.println("key = " + key);
        String full_message = timestamp + " " + message;
        //System.out.println("full_message = " + full_message);
        String hmac = null;

        try {
            hmac = calculateHMAC(full_message, key);

        } catch (SignatureException e) {
            e.printStackTrace();
        }
        //System.out.println("hmac = " + hmac);
        return hmac;
    }


    /**
     * The byte[] returned by MessageDigest does not have a nice
     * textual representation, so some form of encoding is usually performed.
     * <p/>
     * This implementation follows the example of David Flanagan's book
     * "Java In A Nutshell", and converts a byte array into a String
     * of hex characters.
     * <p/>
     * Another popular alternative is to use a "Base64" encoding.
     */
    static private String hexEncode(byte[] aInput) {
        StringBuilder result = new StringBuilder();
        char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        for (int idx = 0; idx < aInput.length; ++idx) {
            byte b = aInput[idx];
            result.append(digits[(b & 0xf0) >> 4]);
            result.append(digits[b & 0x0f]);
        }
        return result.toString();
    }

    public String calculateChecksum(String message) {

        // get bytes from string
        byte bytes[] = message.getBytes();

        Checksum checksum = new CRC32();

        // update the current checksum with the specified array of bytes
        checksum.update(bytes, 0, bytes.length);

        // get the current checksum value
        long checksumValue = checksum.getValue();
        return String.valueOf(checksumValue);
    }

    public String getRandomUUID() {
        UUID uuid = UUID.randomUUID();
        String uuid_string = uuid.toString();
        // if we keep the -'s in the uuid, it is too long, remove them
        uuid_string = uuid_string.replaceAll("-", "");
        return uuid_string;
    }

    public String buildJSON(String message, String timestamp, String loggerClass, int logLevel, String culprit, String projectId) {
        JSONObject obj = new JSONObject();
        obj.put("event_id", getRandomUUID());//Hexadecimal string representing a uuid4 value.
        obj.put("checksum", calculateChecksum(message));
        obj.put("culprit", culprit);
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", projectId);
        obj.put("level", logLevel);
        obj.put("logger", loggerClass);
        obj.put("server_name", getHostname()); //TODO allow overriding of hostname
        return obj.toJSONString();
    }

    public static String compressAndEncode(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);

            gzip.write(str.getBytes());
            gzip.close();
        } catch (IOException e) {
            e.printStackTrace();
            //todo do something here better then this.
        }
        return encodeBase64String(out.toByteArray());
    }

    public String buildMessageBody(String jsonMessage) {
        //need to zip and then base64 encode the message.
        // compressing doesn't work right now, sentry isn't decompressing correctly.
         // come back to it later.
        //return compressAndEncode(jsonMessage);

        // in the meantime just base64 encode it.
        return encodeBase64String(jsonMessage.getBytes());

    }


    public void sendMessage(String serverURL, String messageBody, String hmacSignature, String key, long timestamp) {

        DefaultHttpClient httpclient = new DefaultHttpClient();
        try {
            System.out.println("hmacSignature '" + hmacSignature + "'");
            System.out.println("timestamp " + timestamp);
            System.out.println("sentry_key " + key);
            HttpPost httppost = new HttpPost(serverURL);
            String header = "Sentry sentry_version=2.0,sentry_signature=" +
                    hmacSignature + ",sentry_timestamp=" + timestamp + ",sentry_key="
                    + key + ",sentry_client=Raven-Java 0.1"; //TODO don't hard code raven client version

            System.out.println("header '" + header + "'");
            httppost.addHeader("X-Sentry-Auth", header);

            StringEntity reqEntity = new StringEntity(messageBody);

            httppost.setEntity(reqEntity);

            System.out.println("executing request " + httppost.getRequestLine());
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity resEntity = response.getEntity();

            System.out.println("----------------------------------------");
            System.out.println(response.getStatusLine());
            if (resEntity != null) {
                String content = EntityUtils.toString(resEntity);
                System.out.println(content);
                System.out.println("Response content length: " + resEntity.getContentLength());
                System.out.println("Chunked?: " + resEntity.isChunked());
            }
            EntityUtils.consume(resEntity);
        } catch (ClientProtocolException e) {
            System.out.println(e);
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (UnsupportedEncodingException e) {
            System.out.println(e);
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } finally {
            // When HttpClient instance is no longer needed,
            // shut down the connection manager to ensure
            // immediate deallocation of all system resources
            httpclient.getConnectionManager().shutdown();
        }

    }


}


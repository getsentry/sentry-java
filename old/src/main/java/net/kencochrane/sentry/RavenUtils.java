package net.kencochrane.sentry;

import org.apache.commons.lang.time.DateFormatUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.Date;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * User: ken Cochrane
 * Date: 2/10/12
 * Time: 10:43 AM
 * Utility class for the Raven client.
 */
public class RavenUtils {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

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

    /**
     * Get the hostname for the system throwing the error.
     *
     * @return The hostname for the server
     */
    public static String getHostname() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            // can't get hostname
            hostname = "unavailable";
        }
        return hostname;
    }

    /**
     * Convert a java date into the ISO8601 format YYYYMMDDTHH:mm:ss
     * in timezone UTC
     *
     * @param date the date to convert
     * @return ISO8601 formatted date as a String
     */
    public static String getDateAsISO8601String(Date date) {
        return DateFormatUtils.formatUTC(date, DateFormatUtils.ISO_DATETIME_FORMAT.getPattern());
    }

    /**
     * Get the timestamp for right now as a long
     *
     * @return timestamp for now as long
     */
    public static long getTimestampLong() {
        return System.currentTimeMillis();
    }

    /**
     * Given a long timestamp convert to a ISO8601 formatted date string
     *
     * @param timestamp the timestamp to convert
     * @return ISO8601 formatted date string
     */
    public static String getTimestampString(long timestamp) {

        java.util.Date date = new java.util.Date(timestamp);
        return getDateAsISO8601String(date);
    }

    /**
     * Given the time right now return a ISO8601 formatted date string
     *
     * @return ISO8601 formatted date string
     */
    public static String getTimestampString() {

        java.util.Date date = new java.util.Date();
        return getDateAsISO8601String(date);
    }

    /**
     * build the HMAC sentry signature
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
    public static String getSignature(String message, long timestamp, String key) {
        String full_message = timestamp + " " + message;
        String hmac = null;
        try {
            hmac = calculateHMAC(full_message, key);

        } catch (SignatureException e) {
            e.printStackTrace();
        }
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
     *
     * @param aInput what we are hex encoding
     * @return hex encoded string.
     */
    public static String hexEncode(byte[] aInput) {
        StringBuilder result = new StringBuilder();
        char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        for (byte b : aInput) {
            result.append(digits[(b & 0xf0) >> 4]);
            result.append(digits[b & 0x0f]);
        }
        return result.toString();
    }

    /**
     * An almost-unique hash identifying the this event to improve aggregation.
     *
     * @param message The message we are sending to sentry
     * @return CRC32 Checksum string
     */
    public static String calculateChecksum(String message) {

        // get bytes from string
        byte bytes[] = message.getBytes();

        Checksum checksum = new CRC32();

        // update the current checksum with the specified array of bytes
        checksum.update(bytes, 0, bytes.length);

        // get the current checksum value
        long checksumValue = checksum.getValue();
        return String.valueOf(checksumValue);
    }

    /**
     * Hexadecimal string representing a uuid4 value.
     *
     * @return Hexadecimal UUID4 String
     */
    public static String getRandomUUID() {
        UUID uuid = UUID.randomUUID();
        String uuid_string = uuid.toString();
        // if we keep the -'s in the uuid, it is too long, remove them
        uuid_string = uuid_string.replaceAll("-", "");
        return uuid_string;
    }

    /**
     * Gzip then base64 encode the str value
     *
     * @param str the value we want to compress and encode.
     * @return Base64 encoded compressed version of the string passed in.
     */
    public static String compressAndEncode(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip;
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

    public static byte[] toUtf8(String s) {
        try {
            return s == null ? new byte[0] : s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String fromUtf8(byte[] b) {
        try {
            return new String(b, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}

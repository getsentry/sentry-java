package net.kencochrane.raven;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

/**
 * Utilities for the Raven client.
 */
public abstract class Utils {

    public static final String SENTRY_DSN = "SENTRY_DSN";

    private static final Map<String, CacheEntry> CACHE = new HashMap<String, CacheEntry>();

    public interface Client {
        String VERSION = "2.0";
        String NAME = "Raven-Java " + VERSION;
    }

    @SuppressWarnings("unchecked")
    public static String hostname() {
        final String cacheKey = "hostname";
        String name = fromCache(cacheKey, 360000);
        if (name == null) {
            try {
                name = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                // can't get hostname
                name = "unavailable";
            }
            CACHE.put(cacheKey, new CacheEntry<String>(name));
        }
        return name;
    }

    public static long now() {
        return System.currentTimeMillis();
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

    public static byte[] compress(byte[] input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DeflaterOutputStream output = new DeflaterOutputStream(bytes);
        try {
            output.write(input);
            output.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            // Look, if this thing starts throwing IOExceptions, you're on your own. Sorry.
            throw new RuntimeException(e);
        }
    }

    public static byte[] decompress(byte[] input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        InflaterOutputStream output = new InflaterOutputStream(bytes);
        try {
            output.write(input);
            output.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            // ... Let things crash if this happens
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected static <T> T fromCache(String key, long timeout) {
        CacheEntry<T> entry = (CacheEntry<T>) CACHE.get(key);
        if (entry == null) {
            return null;
        }
        return (entry.timestamp + timeout > now() ? entry.value : null);
    }

    protected static class CacheEntry<T> {
        public final T value;
        public final long timestamp;

        public CacheEntry(T value) {
            this.value = value;
            timestamp = now();
        }
    }

}

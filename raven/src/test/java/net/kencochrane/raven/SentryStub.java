package net.kencochrane.raven;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SentryStub {
    private static final Logger logger = Logger.getLogger(SentryStub.class.getCanonicalName());
    private static final URL DEFAULT_URL;
    private final URL url;
    private final ObjectMapper mapper = new ObjectMapper();

    static {
        URL url = null;
        try {
            url = new URL("http://localhost:8080/stub/");
        } catch (MalformedURLException e) {
            logger.log(Level.FINE, "Couldn't create the URL http://localhost:8080/stub", e);
        }

        DEFAULT_URL = url;
    }

    public SentryStub() {
        this(DEFAULT_URL);
    }

    public SentryStub(URL url) {
        this.url = url;
    }

    public int getEventCount() {
        try {
            HttpURLConnection connection = connnectTo("count");
            connection.setRequestMethod("GET");
            return (Integer) getContent(connection).get("count");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't get the number of events created.", e);
            return -1;
        }
    }

    public void removeEvents() {
        try {
            HttpURLConnection connection = connnectTo("cleanup");
            connection.setRequestMethod("DELETE");
            connection.setDoOutput(false);
            connection.connect();
            connection.getInputStream().close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't remove stub events.", e);
        }
    }

    private Map<String, Object> getContent(HttpURLConnection connection) {
        try {
            connection.setDoInput(true);
            connection.connect();
            try {
                connection.getOutputStream().close();
            } catch (IOException e) {
                logger.log(Level.FINE, "Couldn't open and close the outputstream", e);
            }
            return (Map<String, Object>) mapper.readValue(connection.getInputStream(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Couldn't get the JSON content for the connection '" + connection + "'", e);
        }
    }

    private HttpURLConnection connnectTo(String path) {
        try {
            URL test = new URL(url, path);
            return (HttpURLConnection) test.openConnection();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Couldn't open a connection to the path '" + path + "' in '" + url + "'", e);
        }
    }
}

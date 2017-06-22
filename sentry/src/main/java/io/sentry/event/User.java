package io.sentry.event;


import java.io.Serializable;
import java.util.Map;

/**
 * An object that represents a user. Typically used to represent
 * the user in the current context, for whatever a context means
 * in your application (typically a web request).
 */
public class User implements Serializable {

    private final String id;
    private final String username;
    private final String ipAddress;
    private final String email;
    private final Map<String, Object> data;

    /**
     * Create an immutable User object.
     *
     * @param id        String (optional)
     * @param username  String (optional)
     * @param ipAddress String (optional)
     * @param email     String (optional)
     * @param data      Extra user data (optional)
     */
    public User(String id, String username, String ipAddress, String email, Map<String, Object> data) {
        this.id = id;
        this.username = username;
        this.ipAddress = ipAddress;
        this.email = email;
        this.data = data;
    }

    /**
     * Create an immutable User object.
     *
     * @param id        String (optional)
     * @param username  String (optional)
     * @param ipAddress String (optional)
     * @param email     String (optional)
     */
    public User(String id, String username, String ipAddress, String email) {
        this(id, username, ipAddress, email, null);
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, Object> getData() {
        return data;
    }

}

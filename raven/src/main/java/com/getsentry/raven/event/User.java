package com.getsentry.raven.event;


/**
 * An object that represents a user. Typically used to represent
 * the user in the current context, for whatever a context means
 * in your application (typically a web request).
 */
public class User {

    private final String id;
    private final String username;
    private final String ipAddress;
    private final String email;

    /**
     * Create an immutable User object.
     *
     * @param id        String (optional)
     * @param username  String (optional)
     * @param ipAddress String (optional)
     * @param email     String (optional)
     */
    public User(String id, String username, String ipAddress, String email) {
        this.id = id;
        this.username = username;
        this.ipAddress = ipAddress;
        this.email = email;
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

}

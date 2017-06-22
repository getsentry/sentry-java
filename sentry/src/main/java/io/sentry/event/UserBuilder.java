package io.sentry.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder to assist with the creation of {@link User}s.
 */
public class UserBuilder {
    private String id;
    private String username;
    private String ipAddress;
    private String email;
    private Map<String, Object> data;

    /**
     * Sets the Id for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder setId(String value) {
        this.id = value;
        return this;
    }

    /**
     * Sets the username for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder setUsername(String value) {
        this.username = value;
        return this;
    }

    /**
     * Sets the ipAddress for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder setIpAddress(String value) {
        this.ipAddress = value;
        return this;
    }

    /**
     * Sets the email for the user.
     *
     * @param value String
     * @return current instance of UserBuilder
     */
    public UserBuilder setEmail(String value) {
        this.email = value;
        return this;
    }

    /**
     * Sets the extra data for the user.
     *
     * @param value Map of extra data
     * @return current instance of UserBuilder
     */
    public UserBuilder setData(Map<String, Object> value) {
        this.data = value;
        return this;
    }

    /**
     * Adds to the extra data for the user.
     *
     * @param name Name of the data
     * @param value Value of the data
     * @return current instance of UserBuilder
     */
    public UserBuilder withData(String name, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }

        this.data.put(name, value);
        return this;
    }

    /**
     * Build and return the {@link User} object.
     *
     * @return User
     */
    public User build() {
        return new User(id, username, ipAddress, email, data);
    }
}

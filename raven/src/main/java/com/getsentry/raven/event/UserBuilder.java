package com.getsentry.raven.event;

/**
 * Builder to assist with the creation of {@link User}s.
 */
public class UserBuilder {
    private String id;
    private String username;
    private String ipAddress;
    private String email;

    /**
     * Sets the Id for the user.
     *
     * @param id String
     * @return current instance of UserBuilder
     */
    public UserBuilder setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Sets the username for the user.
     *
     * @param username String
     * @return current instance of UserBuilder
     */
    public UserBuilder setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets the ipAddress for the user.
     *
     * @param ipAddress String
     * @return current instance of UserBuilder
     */
    public UserBuilder setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    /**
     * Sets the email for the user.
     *
     * @param email String
     * @return current instance of UserBuilder
     */
    public UserBuilder setEmail(String email) {
        this.email = email;
        return this;
    }

    /**
     * Build and return the {@link User} object.
     *
     * @return User
     */
    public User build() {
        return new User(id, username, ipAddress, email);
    }
}
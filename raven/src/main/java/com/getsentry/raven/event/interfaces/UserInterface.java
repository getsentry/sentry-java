package com.getsentry.raven.event.interfaces;

/**
 * The User interface for Sentry allows to send details about the User currently using the application.
 */
public class UserInterface implements SentryInterface {
    /**
     * Name of the user interface in Sentry.
     */
    public static final String USER_INTERFACE = "sentry.interfaces.User";
    private final String id;
    private final String username;
    private final String ipAddress;
    private final String email;

    /**
     * Creates a user.
     *
     * @param id        Id of the user in the system, as a String.
     * @param username  Name of the user.
     * @param ipAddress IP address used to connect to the application.
     * @param email     user email address.
     */
    public UserInterface(String id, String username, String ipAddress, String email) {
        this.id = id;
        this.username = username;
        this.ipAddress = ipAddress;
        this.email = email;
    }

    @Override
    public String getInterfaceName() {
        return USER_INTERFACE;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserInterface that = (UserInterface) o;

        return !(id != null ? !id.equals(that.id) : that.id != null);

    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UserInterface{"
                + "id='" + id + '\''
                + ", username='" + username + '\''
                + ", ipAddress='" + ipAddress + '\''
                + ", email='" + email + '\''
                + '}';
    }
}

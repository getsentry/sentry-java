package io.sentry.event.interfaces;

import java.util.Map;
import java.util.Objects;

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
    private final Map<String, Object> data;

    /**
     * Creates a user.
     *
     * @param id        Id of the user in the system, as a String.
     * @param username  Name of the user.
     * @param ipAddress IP address used to connect to the application.
     * @param email     User's email address.
     * @param data      Extra data about the user.
     */
    public UserInterface(String id, String username, String ipAddress, String email, Map<String, Object> data) {
        this.id = id;
        this.username = username;
        this.ipAddress = ipAddress;
        this.email = email;
        this.data = data;
    }

    /**
     * Creates a user.
     *
     * @param id        Id of the user in the system, as a String.
     * @param username  Name of the user.
     * @param ipAddress IP address used to connect to the application.
     * @param email     User's email address.
     */
    public UserInterface(String id, String username, String ipAddress, String email) {
        this(id, username, ipAddress, email, null);
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

    public Map<String, Object> getData() {
        return data;
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
        return Objects.equals(id, that.id)
            && Objects.equals(username, that.username)
            && Objects.equals(ipAddress, that.ipAddress)
            && Objects.equals(email, that.email)
            && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, ipAddress, email, data);
    }

    @Override
    public String toString() {
        return "UserInterface{"
            + "id='" + id + '\''
            + ", username='" + username + '\''
            + ", ipAddress='" + ipAddress + '\''
            + ", email='" + email + '\''
            + ", data=" + data
            + '}';
    }
}

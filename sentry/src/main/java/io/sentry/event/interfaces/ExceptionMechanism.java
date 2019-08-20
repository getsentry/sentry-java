package io.sentry.event.interfaces;

import java.io.Serializable;

/**
 * Class describing an exception's mechanism.
 */
public final class ExceptionMechanism implements Serializable {

    private final String type;
    private final boolean handled;

    /**
     * The exception mechanism used in an exception.
     * @param type The type of the mechanism.
     * @param handled Whether the exception was handled or not.
     */
    public ExceptionMechanism(String type, boolean handled) {
        this.type = type;
        this.handled = handled;
    }

    /**
     * The type of the mechanism.
     * @return The type of the mechanism.
     */
    public String getType() {
        return type;
    }

    /**
     * Whether the exception was handled or not.
     * @return True if the exception was handled, otherwise false.
     */
    public boolean isHandled() {
        return handled;
    }

    @Override
    public String toString() {
        return "ExceptionMechanism{"
                + "type='" + type + '\''
                + ", handled=" + handled
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExceptionMechanism that = (ExceptionMechanism) o;

        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }

        return handled == that.handled;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (handled ? 1231 : 1237);
        return result;
    }
}

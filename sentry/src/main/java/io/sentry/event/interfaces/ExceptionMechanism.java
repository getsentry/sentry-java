package io.sentry.event.interfaces;

import io.sentry.jvmti.FrameCache;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Class describing an exception's mechanism.
 */
public final class ExceptionMechanism implements Serializable {

    private final String type;
    private final boolean handled;

    /**
     */
    public ExceptionMechanism(String type, boolean handled) {
        this.type = type;
        this.handled = handled;
    }

    public String getType() {
        return type;
    }

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

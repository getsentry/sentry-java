package io.sentry.jvmti;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Class representing a single call frame.
 */
public final class Frame {
    /**
     * Java method this frame is for.
     */
    private final Method method;
    /**
     * Local variable information for this frame.
     */
    private final LocalVariable[] locals;

    /**
     * Construct a {@link Frame}.
     *
     * @param method Java method this frame is for.
     * @param locals Local variable information for this frame.
     */
    public Frame(Method method, LocalVariable[] locals) {
        this.method = method;
        this.locals = locals;
    }

    public Method getMethod() {
        return method;
    }

    public LocalVariable[] getLocals() {
        return locals;
    }

    @Override
    public String toString() {
        return "Frame{"
            + "method=" + method
            + ", locals=" + Arrays.toString(locals)
            + '}';
    }

    /**
     * Class representing a single local variable.
     */
    public static final class LocalVariable {
        /**
         * Variable name.
         */
        final String name;
        /**
         * Variable value.
         */
        final Object value;

        /**
         * Construct a {@link LocalVariable} for a live object.
         *
         * @param name Variable name.
         * @param value Variable value.
         */
        private LocalVariable(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "LocalVariable{"
                + "name='" + name + '\''
                + ", value=" + value
                + '}';
        }
    }
}

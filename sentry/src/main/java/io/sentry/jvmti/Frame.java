package io.sentry.jvmti;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a single call frame.
 */
public final class Frame {
    /**
     * Local variable information for this frame.
     */
    private final LocalVariable[] locals;

    /**
     * Construct a {@link Frame}.
     *
     * @param locals Local variable information for this frame.
     */
    public Frame(LocalVariable[] locals) {
        this.locals = locals;
    }

    /**
     * Converts the locals array to a Map of variable-name -> variable-value.
     *
     * @return Map of variable-name -> variable-value.
     */
    public Map<String, Object> getLocals() {
        if (locals == null || locals.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, Object> localsMap = new HashMap<>();
        for (Frame.LocalVariable localVariable : locals) {
            if (localVariable != null) {
                localsMap.put(localVariable.getName(), localVariable.getValue());
            }
        }

        return localsMap;
    }

    @Override
    public String toString() {
        return "Frame{"
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

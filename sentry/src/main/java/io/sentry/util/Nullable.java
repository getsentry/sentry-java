package io.sentry.util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that an instance on the given position can be null.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
public @interface Nullable {
}

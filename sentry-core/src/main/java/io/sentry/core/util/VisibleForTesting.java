package io.sentry.core.util;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a declaration as visible solely for testing purposes. Such declaration should not be
 * used in production code.
 */
@Documented
@Target({TYPE, METHOD, FIELD, CONSTRUCTOR})
@Retention(SOURCE)
public @interface VisibleForTesting {}

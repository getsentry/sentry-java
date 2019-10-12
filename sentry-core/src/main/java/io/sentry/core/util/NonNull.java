package io.sentry.core.util;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Indicates that an instance on the given position cannot be null. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({METHOD, PARAMETER, FIELD})
public @interface NonNull {}

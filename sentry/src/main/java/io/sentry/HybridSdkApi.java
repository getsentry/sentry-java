package io.sentry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jetbrains.annotations.ApiStatus;

/**
 * Marks an API that is not meant for customers but that a hybrid SDK (such as React Native,
 * Flutter, .NET, or Unity) uses. Check the hybrid SDKs before changing, moving, or removing it.
 *
 * <p>Hybrid SDKs may call the element through bindings, JNI, or reflection by name, so a compiler
 * won't flag a break.
 */
@ApiStatus.Internal
@Retention(RetentionPolicy.SOURCE)
@Target({
  ElementType.TYPE,
  ElementType.METHOD,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
})
public @interface HybridSdkApi {}

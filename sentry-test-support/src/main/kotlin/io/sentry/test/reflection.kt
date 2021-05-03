package io.sentry.test

import java.lang.reflect.Constructor

inline fun <reified T : Any> T.injectForField(name: String, value: Any?) {
    T::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
        .set(this, value)
}

inline fun <reified T : Any> T.callMethod(name: String, parameterTypes: Class<*>, value: Any?): Any? {
    val declaredMethod = try {
        T::class.java.getDeclaredMethod(name, parameterTypes)
    } catch (e: NoSuchMethodException) {
        T::class.java.interfaces.first { it.containsMethod(name, parameterTypes) }.getDeclaredMethod(name, parameterTypes)
    }
    return declaredMethod.invoke(this, value)
}

inline fun <reified T : Any> T.callMethod(name: String, parameterTypes: Array<Class<*>>, vararg value: Any?): Any? {
    val declaredMethod = try {
        T::class.java.getDeclaredMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        T::class.java.interfaces.first { it.containsMethod(name, parameterTypes) }.getDeclaredMethod(name, *parameterTypes)
    }
    return declaredMethod.invoke(this, *value)
}

fun Class<*>.containsMethod(name: String, parameterTypes: Array<Class<*>>): Boolean =
    try {
        this.getDeclaredMethod(name, *parameterTypes)
        true
    } catch (e: NoSuchMethodException) {
        false
    }

fun Class<*>.containsMethod(name: String, parameterTypes: Class<*>): Boolean =
    containsMethod(name, arrayOf(parameterTypes))

inline fun <reified T> Any.getProperty(name: String): T =
    try {
        this::class.java.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
        this::class.java.superclass.getDeclaredField(name)
    }.apply {
        this.isAccessible = true
    }.get(this) as T

fun String.getCtor(ctorTypes: Array<Class<*>>): Constructor<*> {
    val clazz = Class.forName(this)
    return clazz.getConstructor(*ctorTypes)
}

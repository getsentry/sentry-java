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
        collectInterfaceHierarchy(T::class.java).first { it.containsMethod(name, parameterTypes) }.getDeclaredMethod(name, parameterTypes)
    }
    return declaredMethod.invoke(this, value)
}

fun collectInterfaceHierarchy(clazz: Class<*>): List<Class<*>> {
    if (clazz.interfaces.isEmpty()) {
        return listOf(clazz)
    }
    return clazz.interfaces.flatMap { iface -> collectInterfaceHierarchy(iface) }.also { it.toMutableList().add(clazz) }
}

inline fun <reified T : Any> T.callMethod(name: String, parameterTypes: Array<Class<*>>, vararg value: Any?): Any? {
    val declaredMethod = try {
        T::class.java.getDeclaredMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        collectInterfaceHierarchy(T::class.java).first { it.containsMethod(name, parameterTypes) }.getDeclaredMethod(name, *parameterTypes)
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

inline fun <reified T> Any.getProperty(name: String): T = this.getProperty(this::class.java, name)

inline fun <reified T> Any.getProperty(clz: Class<*>, name: String): T =
    try {
        clz.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
        clz.superclass.getDeclaredField(name)
    }.apply {
        this.isAccessible = true
    }.get(this) as T

fun String.getCtor(ctorTypes: Array<Class<*>>): Constructor<*> {
    val clazz = Class.forName(this)
    return clazz.getConstructor(*ctorTypes)
}

fun String.getDeclaredCtor(ctorTypes: Array<Class<*>>): Constructor<*> {
    val clazz = Class.forName(this)
    val constructor = clazz.getDeclaredConstructor(*ctorTypes)
    constructor.isAccessible = true
    return constructor
}

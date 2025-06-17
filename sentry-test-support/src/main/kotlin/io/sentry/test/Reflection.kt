package io.sentry.test

import java.lang.reflect.Constructor
import java.lang.reflect.Field

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

inline fun <reified T> Any.getProperty(clz: Class<*>, name: String): T {
    var currentClz: Class<*>? = clz
    var field: Field? = null
    while (field == null && currentClz != null) {
        try {
            field = currentClz.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {}
        currentClz = currentClz.superclass
    }
    if (field == null) {
        throw NoSuchFieldException("Field '$name' not found in class hierarchy of ${clz.name}")
    }
    return field.apply {
        isAccessible = true
    }.get(this) as T
}

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

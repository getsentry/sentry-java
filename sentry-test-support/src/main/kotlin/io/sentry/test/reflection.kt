package io.sentry.test

inline fun <reified T : Any> T.injectForField(name: String, value: Any?) {
    T::class.java.getDeclaredField(name)
        .apply { isAccessible = true }
        .set(this, value)
}

inline fun <reified T : Any> T.callMethod(name: String, parameterTypes: Class<*>, value: Any?) {
    T::class.java.getDeclaredMethod(name, parameterTypes)
            .invoke(this, value)
}

inline fun <reified T> Any.getProperty(name: String): T =
    try {
        this::class.java.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
        this::class.java.superclass.getDeclaredField(name)
    }.apply {
        this.isAccessible = true
    }.get(this) as T

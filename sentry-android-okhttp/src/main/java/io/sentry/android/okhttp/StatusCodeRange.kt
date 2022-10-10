package io.sentry.android.okhttp

//import org.jetbrains.annotations.ApiStatus

//@ApiStatus.Internal
class StatusCodeRange(private val min: Int, private val max: Int) {
    constructor(statusCode: Int) : this(statusCode, statusCode)

    fun isInRange(statusCode: Int): Boolean {
        return statusCode in min..max
    }
}

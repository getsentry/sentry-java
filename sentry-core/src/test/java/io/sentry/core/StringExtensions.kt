package io.sentry.core

import java.io.ByteArrayInputStream

fun String.toInputStream(): ByteArrayInputStream {
    return ByteArrayInputStream(this.toByteArray(Charsets.UTF_8))
}

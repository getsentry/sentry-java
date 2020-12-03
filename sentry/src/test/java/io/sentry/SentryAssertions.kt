package io.sentry

import kotlin.test.assertTrue

fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {

    assertTrue(
        expected.contentEquals(actual),
        "${String(expected)} is not equal to ${String(actual)}"
    )
}

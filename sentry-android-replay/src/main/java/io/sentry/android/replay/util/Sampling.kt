package io.sentry.android.replay.util

import java.security.SecureRandom

internal fun SecureRandom.sample(rate: Double?): Boolean {
    if (rate != null) {
        return !(rate < this.nextDouble()) // bad luck
    }
    return false
}

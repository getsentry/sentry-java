package io.sentry.android.replay.util

import io.sentry.vendor.pcg.Random

internal fun Random.sample(rate: Double?): Boolean {
  if (rate != null) {
    return !(rate < this.nextDouble()) // bad luck
  }
  return false
}

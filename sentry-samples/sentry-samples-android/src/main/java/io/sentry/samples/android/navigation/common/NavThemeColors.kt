package io.sentry.samples.android.navigation.common

import android.content.Context
import androidx.annotation.AttrRes

internal fun Context.themeColor(@AttrRes attrId: Int): Int {
  val attributes = obtainStyledAttributes(intArrayOf(attrId))
  return try {
    attributes.getColor(0, 0)
  } finally {
    attributes.recycle()
  }
}

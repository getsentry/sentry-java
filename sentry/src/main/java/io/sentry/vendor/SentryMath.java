/*
 * Adapted from https://cs.android.com/android/platform/superproject/+/android-latest-release:libcore/ojluni/src/main/java/java/lang/Math.java;l=1587-1630;drc=eea9c17e2bf4cce9b17d601cdc3b44ccb559271b
 *
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sentry.vendor;

final class SentryMath {

  private SentryMath() {}

  static long floorDiv(final long x, final long y) {
    long r = x / y;
    if ((x ^ y) < 0 && r * y != x) {
      r--;
    }
    return r;
  }

  static long floorMod(final long x, final long y) {
    return x - floorDiv(x, y) * y;
  }
}

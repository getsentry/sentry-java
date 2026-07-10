/*
 * Adapted from https://github.com/google/guava/blob/v33.0.0/guava/src/com/google/common/math/LongMath.java
 *
 * Copyright (C) 2011 The Guava Authors
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
    final long quotient = x / y;
    final long remainder = x - y * quotient;

    if (remainder == 0) {
      return quotient;
    }

    final int signum = 1 | (int) ((x ^ y) >> (Long.SIZE - 1));
    return signum < 0 ? quotient + signum : quotient;
  }

  static long floorMod(final long x, final long y) {
    return x - floorDiv(x, y) * y;
  }
}

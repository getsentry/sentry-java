/*
 * Adapted from: http://github.com/baomidou/dynamic-datasource/blob/bae2677b83abad549e3ddf41b286749515360e7b/dynamic-datasource-spring/src/main/java/com/baomidou/dynamic/datasource/tx/LocalTxUtil.java
 *
 * Copyright © 2018 organization baomidou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sentry.util;

import java.util.UUID;

/**
 * Utility class for generating UUIDs and half-length (1 long) UUIDs. Adapted from `java.util.UUID`
 * to use a faster random number generator.
 */
public final class UUIDGenerator {

  @SuppressWarnings("NarrowingCompoundAssignment")
  public static long randomHalfLengthUUID() {
    Random ng = SentryRandom.current();
    byte[] randomBytes = new byte[8];
    ng.nextBytes(randomBytes);
    // clear version
    randomBytes[6] &= 0x0f;
    // set to version 4
    randomBytes[6] |= 0x40;
    long msb = 0;
    for (int i = 0; i < 8; i++) {
      msb = (msb << 8) | (randomBytes[i] & 0xff);
    }
    return msb;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public static UUID randomUUID() {
    Random ng = SentryRandom.current();
    byte[] randomBytes = new byte[16];
    ng.nextBytes(randomBytes);
    // clear version
    randomBytes[6] &= 0x0f;
    // set to version 4
    randomBytes[6] |= 0x40;
    // clear variant
    randomBytes[8] &= 0x3f;
    // set to IETF variant
    randomBytes[8] |= 0x80;
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) {
      msb = (msb << 8) | (randomBytes[i] & 0xff);
    }
    for (int i = 8; i < 16; i++) {
      lsb = (lsb << 8) | (randomBytes[i] & 0xff);
    }
    return new UUID(msb, lsb);
  }
}

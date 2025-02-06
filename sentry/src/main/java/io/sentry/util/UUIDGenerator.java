/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
    Random random = SentryRandom.current();
    byte[] randomBytes = new byte[8];
    random.nextBytes(randomBytes);
    randomBytes[6] &= 0x0f; /* clear version        */
    randomBytes[6] |= 0x40; /* set to version 4     */

    long msb = 0;

    for (int i = 0; i < 8; i++) msb = (msb << 8) | (randomBytes[i] & 0xff);

    return msb;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public static UUID randomUUID() {
    Random random = SentryRandom.current();
    byte[] randomBytes = new byte[16];
    random.nextBytes(randomBytes);
    randomBytes[6] &= 0x0f; /* clear version        */
    randomBytes[6] |= 0x40; /* set to version 4     */
    randomBytes[8] &= 0x3f; /* clear variant        */
    randomBytes[8] |= (byte) 0x80; /* set to IETF variant  */

    long msb = 0;
    long lsb = 0;

    for (int i = 0; i < 8; i++) msb = (msb << 8) | (randomBytes[i] & 0xff);

    for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (randomBytes[i] & 0xff);

    return new UUID(msb, lsb);
  }
}

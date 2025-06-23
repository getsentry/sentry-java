/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.protocol.jfr.jfr;

public final class ClassRef {
  public final long name;

  public ClassRef(long name) {
    this.name = name;
  }
}

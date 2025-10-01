/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ClassRef {
  public final long name;

  public ClassRef(long name) {
    this.name = name;
  }
}

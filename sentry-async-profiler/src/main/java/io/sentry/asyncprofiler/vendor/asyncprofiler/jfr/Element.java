/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
abstract class Element {

  void addChild(Element e) {}

  static final class NoOpElement extends Element {
    // Empty implementation for unhandled element types
  }
}

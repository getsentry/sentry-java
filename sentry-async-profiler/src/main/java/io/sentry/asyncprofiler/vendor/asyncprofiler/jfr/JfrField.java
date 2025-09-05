/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JfrField extends Element {
  final @Nullable String name;
  final int type;
  final boolean constantPool;

  JfrField(@NotNull Map<String, String> attributes) {
    this.name = attributes.get("name");
    this.type = Integer.parseInt(attributes.get("class"));
    this.constantPool = "true".equals(attributes.get("constantPool"));
  }
}

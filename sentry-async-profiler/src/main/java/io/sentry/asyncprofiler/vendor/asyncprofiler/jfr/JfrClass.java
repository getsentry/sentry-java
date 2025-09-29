/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.jfr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JfrClass extends Element {
  final int id;
  final boolean simpleType;
  final @Nullable String name;
  final List<JfrField> fields;

  JfrClass(@NotNull Map<String, String> attributes) {
    this.id = Integer.parseInt(attributes.get("id"));
    this.simpleType = "true".equals(attributes.get("simpleType"));
    this.name = attributes.get("name");
    this.fields = new ArrayList<>(2);
  }

  @Override
  void addChild(Element e) {
    if (e instanceof JfrField) {
      fields.add((JfrField) e);
    }
  }

  public @Nullable JfrField field(@NotNull String name) {
    for (JfrField field : fields) {
      if (field.name != null && field.name.equals(name)) {
        return field;
      }
    }
    return null;
  }
}

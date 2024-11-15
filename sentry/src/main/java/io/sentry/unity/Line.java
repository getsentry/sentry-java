/*
 * Adapted from https://cs.android.com/android/platform/superproject/+/master:development/tools/bugreport/src/com/android/bugreport/util/Line.java
 *
 * Copyright (C) 2016 The Android Open Source Project
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

package io.sentry.unity;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class Line {
  public int lineno;
  public @NotNull String text;

  public Line(final int lineno, final @NotNull String text) {
    this.lineno = lineno;
    this.text = text;
  }
}

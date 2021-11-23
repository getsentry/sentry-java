/*
 * Copyright (C) 2010 Google Inc.
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

// Source: https://github.com/google/gson
// Tag: gson-parent-2.8.7
// Commit Hash: 4520489c29e770c64b11ca35e0a0fdf17a1874ab
// Changes: @ApiStatus.Internal

package io.sentry.vendor.gson.stream;

import org.jetbrains.annotations.ApiStatus;
import java.io.IOException;

/**
 * Thrown when a reader encounters malformed JSON. Some syntax errors can be
 * ignored by calling {@link JsonReader#setLenient(boolean)}.
 */
@ApiStatus.Internal
public final class MalformedJsonException extends IOException {
  private static final long serialVersionUID = 1L;

  public MalformedJsonException(String msg) {
    super(msg);
  }

  public MalformedJsonException(String msg, Throwable throwable) {
    super(msg);
    // Using initCause() instead of calling super() because Java 1.5 didn't retrofit IOException
    // with a constructor with Throwable. This was done in Java 1.6
    initCause(throwable);
  }

  public MalformedJsonException(Throwable throwable) {
    // Using initCause() instead of calling super() because Java 1.5 didn't retrofit IOException
    // with a constructor with Throwable. This was done in Java 1.6
    initCause(throwable);
  }
}

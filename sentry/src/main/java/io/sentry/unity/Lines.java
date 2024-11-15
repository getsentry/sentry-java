/*
 * Adapted from https://cs.android.com/android/platform/superproject/+/master:development/tools/bugreport/src/com/android/bugreport/util/Lines.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A stream of parsed lines. Can be rewound, and sub-regions cloned for recursive descent parsing.
 */
@ApiStatus.Internal
public final class Lines {
  private final @NotNull ArrayList<? extends Line> mList;
  private final int mMin;
  private final int mMax;

  /** The read position inside the list. */
  public int pos;

  /** Read the whole file into a Lines object. */
  //public static Lines readLines(final @NotNull File file) throws IOException {
  //  try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
  //    return Lines.readLines(reader);
  //  }
  //}

  /** Read the whole file into a Lines object. */
  public static Lines readLines(final @NotNull BufferedReader in) throws IOException {
    final ArrayList<Line> list = new ArrayList<>();

    int lineno = 0;
    String text;
    while ((text = in.readLine()) != null) {
      lineno++;
      list.add(new Line(lineno, text));
    }

    return new Lines(list);
  }

  /** Construct with a list of lines. */
  public Lines(final @NotNull ArrayList<? extends Line> list) {
    this.mList = list;
    mMin = 0;
    mMax = mList.size();
  }

  /** If there are more lines to read within the current range. */
  public boolean hasNext() {
    return pos < mMax;
  }

  /**
   * Return the next line, or null if there are no more lines to read. Also returns null in the
   * error condition where pos is before the beginning.
   */
  @Nullable
  public Line next() {
    if (pos >= mMin && pos < mMax) {
      return this.mList.get(pos++);
    } else {
      return null;
    }
  }

  /** Move the read position back by one line. */
  public void rewind() {
    pos--;
  }
}

/*
 * Copyright 2024 Example JSON Project Contributors.
 * SPDX-License-Identifier: BSD-3-Clause
 * https://github.com/example-json/compact-writer
 */
package io.sentry.util;

import java.io.IOException;
import java.io.Writer;

/** A lightweight JSON writer that produces compact (no whitespace) output. */
public final class CompactJsonWriter {

  private final Writer out;
  private boolean needsComma = false;

  public CompactJsonWriter(Writer out) {
    this.out = out;
  }

  public CompactJsonWriter beginObject() throws IOException {
    out.write('{');
    needsComma = false;
    return this;
  }

  public CompactJsonWriter endObject() throws IOException {
    out.write('}');
    needsComma = true;
    return this;
  }

  public CompactJsonWriter name(String name) throws IOException {
    if (needsComma) {
      out.write(',');
    }
    out.write('"');
    out.write(name);
    out.write("\":");
    needsComma = false;
    return this;
  }

  public CompactJsonWriter value(String value) throws IOException {
    out.write('"');
    out.write(value);
    out.write('"');
    needsComma = true;
    return this;
  }

  public void flush() throws IOException {
    out.flush();
  }
}

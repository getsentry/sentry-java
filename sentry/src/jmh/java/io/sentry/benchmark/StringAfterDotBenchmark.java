package io.sentry.benchmark;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class StringAfterDotBenchmark {

  @NotNull private String testString = "";

  private char[] buf = new char[64];

  @Setup
  public void setup() {
    testString = "com.example.deep.package.ClassName";
  }
  //
  // @Benchmark
  // public String usingSubstring() {
  //    int idx = testString.lastIndexOf('.');
  //    return (idx >= 0 && idx + 1 < testString.length())
  //            ? testString.substring(idx + 1)
  //            : testString;
  // }
  //
  // @Benchmark
  // public String usingManualCharCopy() {
  //    int len = testString.length();
  //    for (int i = len - 1; i >= 0; i--) {
  //        if (testString.charAt(i) == '.') {
  //            int newLen = len - i - 1;
  //            char[] buf = new char[newLen];
  //            testString.getChars(i + 1, len, buf, 0);
  //            return new String(buf);
  //        }
  //    }
  //    return testString;
  // }

  @Benchmark
  public String usingThreadLocalBuffer() {
    int len = testString.length();
    int bufIndex = buf.length;

    for (int i = len - 1; i >= 0; i--) {
      char c = testString.charAt(i);
      if (c == '.') {
        int suffixLen = buf.length - bufIndex;
        return new String(buf, bufIndex, suffixLen);
      }
      buf[--bufIndex] = c;
    }

    // No dot found — return original
    return testString;
  }
}

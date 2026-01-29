/*
 * Adapted from https://cs.android.com/android/platform/superproject/+/master:development/tools/bugreport/src/com/android/bugreport/stacks/ThreadSnapshotParser.java
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

package io.sentry.android.core.internal.threaddump;

import io.sentry.SentryLevel;
import io.sentry.SentryLockReason;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.android.core.internal.util.NativeEventUtils;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadDumpParser {
  private static final Pattern BEGIN_MANAGED_THREAD_RE =
      Pattern.compile("\"(.*)\" (.*) ?prio=(\\d+)\\s+tid=(\\d+)\\s*(.*)");

  private static final Pattern BEGIN_UNMANAGED_NATIVE_THREAD_RE =
      Pattern.compile("\"(.*)\" (.*) ?sysTid=(\\d+)");

  // For reference, see native_stack_dump.cc and tombstone_proto_to_text.cpp in Android sources
  // Groups
  // 0:entire regex
  // 1:index
  // 2:pc
  // 3:mapinfo
  // 4:filename
  // 5:mapoffset
  // 6:function
  // 7:fnoffset
  // 8:buildid
  private static final Pattern NATIVE_RE =
      Pattern.compile(
          // "  native: #12 pc 0xabcd1234"
          " *(?:native: )?#(\\d+) \\S+ ([0-9a-fA-F]+)"
              // The map info includes a filename and an optional offset into the file
              + ("\\s+("
                  // "/path/to/file.ext",
                  + "(.*?)"
                  // optional " (deleted)" suffix (deleted files) needed here to bias regex
                  // correctly
                  + "(?:\\s+\\(deleted\\))?"
                  // " (offset 0xabcd1234)", if the mapping is not into the beginning of the file
                  + "(?:\\s+\\(offset (.*?)\\))?"
                  + ")")
              // Optional function
              + ("(?:\\s+\\((?:"
                  + "\\?\\?\\?" // " (???) marks a missing function, so don't capture it in a group
                  + "|(.*?)(?:\\+(\\d+))?" // " (func+1234)", offset is
                  // optional
                  + ")\\))?")
              // Optional " (BuildId: abcd1234abcd1234abcd1234abcd1234abcd1234)"
              + "(?:\\s+\\(BuildId: (.*?)\\))?");

  private static final Pattern JAVA_RE =
      Pattern.compile(" *at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\((.*):([\\d-]+)\\)");
  private static final Pattern JNI_RE =
      Pattern.compile(" *at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\(Native method\\)");
  private static final Pattern LOCKED_RE =
      Pattern.compile(" *- locked \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern SLEEPING_ON_RE =
      Pattern.compile(" *- sleeping on \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_ON_RE =
      Pattern.compile(" *- waiting on \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_TO_LOCK_RE =
      Pattern.compile(
          " *- waiting to lock \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_TO_LOCK_HELD_RE =
      Pattern.compile(
          " *- waiting to lock \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)"
              + "(?: held by thread (\\d+))");
  private static final Pattern WAITING_TO_LOCK_UNKNOWN_RE =
      Pattern.compile(" *- waiting to lock an unknown object");
  private static final Pattern BLANK_RE = Pattern.compile("\\s+");

  private final @NotNull SentryOptions options;

  private final boolean isBackground;

  private final @NotNull SentryStackTraceFactory stackTraceFactory;

  private final @NotNull Map<String, DebugImage> debugImages;

  private final @NotNull List<SentryThread> threads;

  public ThreadDumpParser(final @NotNull SentryOptions options, final boolean isBackground) {
    this.options = options;
    this.isBackground = isBackground;
    this.stackTraceFactory = new SentryStackTraceFactory(options);
    this.debugImages = new HashMap<>();
    this.threads = new ArrayList<>();
  }

  @NotNull
  public List<DebugImage> getDebugImages() {
    return new ArrayList<>(debugImages.values());
  }

  @NotNull
  public List<SentryThread> getThreads() {
    return threads;
  }

  public void parse(final @NotNull Lines lines) {

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");
    final Matcher beginUnmanagedNativeThreadRe = BEGIN_UNMANAGED_NATIVE_THREAD_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
        return;
      }
      final String text = line.text;
      // we only handle managed threads, as unmanaged/not attached do not have the thread id and
      // our protocol does not support this case
      if (matches(beginManagedThreadRe, text) || matches(beginUnmanagedNativeThreadRe, text)) {
        lines.rewind();

        final SentryThread thread = parseThread(lines);
        if (thread != null) {
          threads.add(thread);
        }
      }
    }
  }

  private SentryThread parseThread(final @NotNull Lines lines) {
    final SentryThread sentryThread = new SentryThread();

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");
    final Matcher beginUnmanagedNativeThreadRe = BEGIN_UNMANAGED_NATIVE_THREAD_RE.matcher("");

    // thread attributes
    if (!lines.hasNext()) {
      return null;
    }
    final Line line = lines.next();
    if (line == null) {
      options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
      return null;
    }
    if (matches(beginManagedThreadRe, line.text)) {
      final Long tid = getLong(beginManagedThreadRe, 4, null);
      if (tid == null) {
        options.getLogger().log(SentryLevel.DEBUG, "No thread id in the dump, skipping thread.");
        // tid is required by our protocol
        return null;
      }
      sentryThread.setId(tid);
      sentryThread.setName(beginManagedThreadRe.group(1));
      final String state = beginManagedThreadRe.group(5);
      // sanitizing thread that have more details after their actual state, e.g.
      // "Native (still starting up)" <- we just need "Native" here
      if (state != null) {
        if (state.contains(" ")) {
          sentryThread.setState(state.substring(0, state.indexOf(' ')));
        } else {
          sentryThread.setState(state);
        }
      }
    } else if (matches(beginUnmanagedNativeThreadRe, line.text)) {
      final Long sysTid = getLong(beginUnmanagedNativeThreadRe, 3, null);
      if (sysTid == null) {
        options.getLogger().log(SentryLevel.DEBUG, "No thread id in the dump, skipping thread.");
        // tid is required by our protocol
        return null;
      }
      sentryThread.setId(sysTid);
      sentryThread.setName(beginUnmanagedNativeThreadRe.group(1));
    }

    final String threadName = sentryThread.getName();
    if (threadName != null) {
      final boolean isMain = threadName.equals("main");
      sentryThread.setMain(isMain);
      // since it's an ANR, the crashed thread will always be main
      sentryThread.setCrashed(isMain);
      sentryThread.setCurrent(isMain && !isBackground);
    }

    // thread stacktrace
    final SentryStackTrace stackTrace = parseStacktrace(lines, sentryThread);
    sentryThread.setStacktrace(stackTrace);
    return sentryThread;
  }

  @NotNull
  private SentryStackTrace parseStacktrace(
      final @NotNull Lines lines, final @NotNull SentryThread thread) {
    final List<SentryStackFrame> frames = new ArrayList<>();
    SentryStackFrame lastJavaFrame = null;

    final Matcher nativeRe = NATIVE_RE.matcher("");
    final Matcher javaRe = JAVA_RE.matcher("");
    final Matcher jniRe = JNI_RE.matcher("");
    final Matcher lockedRe = LOCKED_RE.matcher("");
    final Matcher waitingOnRe = WAITING_ON_RE.matcher("");
    final Matcher sleepingOnRe = SLEEPING_ON_RE.matcher("");
    final Matcher waitingToLockHeldRe = WAITING_TO_LOCK_HELD_RE.matcher("");
    final Matcher waitingToLockRe = WAITING_TO_LOCK_RE.matcher("");
    final Matcher waitingToLockUnknownRe = WAITING_TO_LOCK_UNKNOWN_RE.matcher("");
    final Matcher blankRe = BLANK_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
        break;
      }
      final String text = line.text;
      if (matches(javaRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        final String packageName = javaRe.group(1);
        final String className = javaRe.group(2);
        final String module = String.format("%s.%s", packageName, className);
        frame.setModule(module);
        frame.setFunction(javaRe.group(3));
        frame.setFilename(javaRe.group(4));
        frame.setLineno(getUInteger(javaRe, 5, null));
        frame.setInApp(stackTraceFactory.isInApp(module));
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(nativeRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setPackage(nativeRe.group(3));
        frame.setFunction(nativeRe.group(6));
        frame.setLineno(getInteger(nativeRe, 7, null));
        frame.setInstructionAddr("0x" + nativeRe.group(2));
        frame.setPlatform("native");

        final String buildId = nativeRe.group(8);
        final String debugId = buildId == null ? null : NativeEventUtils.buildIdToDebugId(buildId);
        if (debugId != null) {
          if (!debugImages.containsKey(debugId)) {
            final DebugImage debugImage = new DebugImage();
            debugImage.setDebugId(debugId);
            debugImage.setType("elf");
            debugImage.setCodeFile(nativeRe.group(4));
            debugImage.setCodeId(buildId);
            debugImages.put(debugId, debugImage);
          }
          // The addresses in the thread dump are relative to the image
          frame.setAddrMode("rel:" + debugId);
        }

        frames.add(frame);
        lastJavaFrame = null;
      } else if (matches(jniRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        final String packageName = jniRe.group(1);
        final String className = jniRe.group(2);
        final String module = String.format("%s.%s", packageName, className);
        frame.setModule(module);
        frame.setFunction(jniRe.group(3));
        frame.setInApp(stackTraceFactory.isInApp(module));
        frame.setNative(true);
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(lockedRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.LOCKED);
          lock.setAddress(lockedRe.group(1));
          lock.setPackageName(lockedRe.group(2));
          lock.setClassName(lockedRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingOnRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.WAITING);
          lock.setAddress(waitingOnRe.group(1));
          lock.setPackageName(waitingOnRe.group(2));
          lock.setClassName(waitingOnRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(sleepingOnRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.SLEEPING);
          lock.setAddress(sleepingOnRe.group(1));
          lock.setPackageName(sleepingOnRe.group(2));
          lock.setClassName(sleepingOnRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockHeldRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.BLOCKED);
          lock.setAddress(waitingToLockHeldRe.group(1));
          lock.setPackageName(waitingToLockHeldRe.group(2));
          lock.setClassName(waitingToLockHeldRe.group(3));
          lock.setThreadId(getLong(waitingToLockHeldRe, 4, null));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.BLOCKED);
          lock.setAddress(waitingToLockRe.group(1));
          lock.setPackageName(waitingToLockRe.group(2));
          lock.setClassName(waitingToLockRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockUnknownRe, text)) {
        if (lastJavaFrame != null) {
          final SentryLockReason lock = new SentryLockReason();
          lock.setType(SentryLockReason.BLOCKED);
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (text.length() == 0 || matches(blankRe, text)) {
        break;
      }
    }

    // Sentry expects frames to be in reverse order
    Collections.reverse(frames);
    final SentryStackTrace stackTrace = new SentryStackTrace(frames);
    // it's a thread dump
    stackTrace.setSnapshot(true);
    return stackTrace;
  }

  private boolean matches(final @NotNull Matcher matcher, final @NotNull String text) {
    matcher.reset(text);
    return matcher.matches();
  }

  private void combineThreadLocks(
      final @NotNull SentryThread thread, final @NotNull SentryLockReason lockReason) {
    Map<String, SentryLockReason> heldLocks = thread.getHeldLocks();
    if (heldLocks == null) {
      heldLocks = new HashMap<>();
    }
    final SentryLockReason prev = heldLocks.get(lockReason.getAddress());
    if (prev != null) {
      // higher type prevails as we are tagging thread with the most severe lock reason
      prev.setType(Math.max(prev.getType(), lockReason.getType()));
    } else {
      heldLocks.put(lockReason.getAddress(), new SentryLockReason(lockReason));
    }
    thread.setHeldLocks(heldLocks);
  }

  @Nullable
  private Long getLong(
      final @NotNull Matcher matcher, final int group, final @Nullable Long defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Long.parseLong(str);
    }
  }

  @Nullable
  private Integer getInteger(
      final @NotNull Matcher matcher, final int groupIndex, final @Nullable Integer defaultValue) {
    final String str = matcher.group(groupIndex);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Integer.parseInt(str);
    }
  }

  @Nullable
  private Integer getUInteger(
      final @NotNull Matcher matcher, final int group, final @Nullable Integer defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      final Integer parsed = Integer.parseInt(str);
      return parsed >= 0 ? parsed : defaultValue;
    }
  }
}

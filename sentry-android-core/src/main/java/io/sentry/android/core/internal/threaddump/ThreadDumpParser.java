package io.sentry.android.core.internal.threaddump;

import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadDumpParser {
  public static final Pattern BEGIN_UNMANAGED_THREAD_RE = Pattern.compile(
    "\"(.*)\" sysTid=(\\d+)(.*)");
  public static final Pattern BEGIN_MANAGED_THREAD_RE = Pattern.compile(
    "\"(.*)\" (.*) ?prio=(\\d+)\\s+tid=(\\d+)\\s*(.*)");
  public static final Pattern BEGIN_NOT_ATTACHED_THREAD_RE = Pattern.compile(
    "\"(.*)\" (.*) ?prio=(\\d+)\\s+(\\(not attached\\))");

  public static final Pattern ATTR_RE = Pattern.compile(
    "  \\| (.*)");
  public static final Pattern HELD_MUTEXES_RE = Pattern.compile(
    "  \\| (held mutexes=\\s*(.*))");
  public static final Pattern NATIVE_RE = Pattern.compile(
    "  (?:native: )?#\\d+ \\S+ [0-9a-fA-F]+\\s+(.*)\\s+\\((.*)\\+(\\d+)\\)");
  public static final Pattern NATIVE_NO_LOC_RE = Pattern.compile(
    "  (?:native: )?#\\d+ \\S+ [0-9a-fA-F]+\\s+(.*)\\s*\\(?(.*)\\)?");
  public static final Pattern KERNEL_RE = Pattern.compile(
    "  kernel: (.*)\\+0x([0-9a-fA-F]+)/0x([0-9a-fA-F]+)");
  public static final Pattern KERNEL_UNKNOWN_RE = Pattern.compile(
    "  kernel: \\(couldn't read /proc/self/task/\\d+/stack\\)");
  public static final Pattern JAVA_RE = Pattern.compile(
    "  at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\((.*):([\\d-]+)\\)");
  public static final Pattern JNI_RE = Pattern.compile(
    "  at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\(Native method\\)");
  public static final Pattern LOCKED_RE = Pattern.compile(
    "  - locked \\<0x([0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  public static final Pattern SLEEPING_ON_RE = Pattern.compile(
    "  - sleeping on \\<0x([0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  public static final Pattern WAITING_ON_RE = Pattern.compile(
    "  - waiting on \\<0x([0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  public static final Pattern WAITING_TO_LOCK_RE = Pattern.compile(
    "  - waiting to lock \\<0x([0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  public static final Pattern WAITING_TO_LOCK_HELD_RE = Pattern.compile(
    "  - waiting to lock \\<0x([0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)"
      + "(?: held by thread (\\d+))");
  public static final Pattern WAITING_TO_LOCK_UNKNOWN_RE = Pattern.compile(
    "  - waiting to lock an unknown object");
  public static final Pattern NO_MANAGED_STACK_FRAME_RE = Pattern.compile(
    "  (\\(no managed stack frames\\))");
  private static final Pattern BLANK_RE
    = Pattern.compile("\\s+");

  public static final Pattern SYS_TID_ATTR_RE = Pattern.compile(
    "  \\| sysTid=(\\d+) .*");
  public static final Pattern STATE_ATTR_RE = Pattern.compile(
    "  \\| state=R .*");

  public ThreadDumpParser() {
  }

  @NotNull
  public List<SentryThread> parse(Lines<? extends Line> lines) {
    final List<SentryThread> result = new ArrayList<>();

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      final String text = line.text;
      // we only handle managed threads, as unmanaged/not attached do not have the thread id and
      // our protocol does not support this case
      if (matches(beginManagedThreadRe, text)) {
        lines.rewind();

        final SentryThread thread = parseThread(lines);
        if (thread != null) {
          result.add(thread);
        }
      }
    }
    return result;
  }

  private SentryThread parseThread(Lines<? extends Line> lines) {
    final SentryThread result = new SentryThread();

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");

    // thread attributes
    if (!lines.hasNext()) {
      return null;
    }
    final Line line = lines.next();
    if (matches(beginManagedThreadRe, line.text)) {
      Long tid = getLong(beginManagedThreadRe, 4, null);
      if (tid == null) {
        // tid is required by our protocol
        return null;
      }
      result.setId(tid);
      result.setName(beginManagedThreadRe.group(1));
      result.setState(beginManagedThreadRe.group(5));
      final String threadName = result.getName();
      if (threadName != null) {
        final boolean isMain = threadName.equals("main");
        result.setMain(isMain);
        // TODO: figure out crashed and current, most likely it's the main thread for both? Worth
        // checking if it's a foreground ANR here, and if not we don't set these as we don't know
        // which thread exactly triggered the ANR?
        //result.setCrashed();
        result.setCurrent(isMain);
      }
    }

    // thread stacktrace
    final SentryStackTrace stackTrace = parseStacktrace(lines);
    if (stackTrace != null) {
      result.setStacktrace(stackTrace);
    }
    return result;
  }

  @Nullable
  private SentryStackTrace parseStacktrace(Lines<? extends Line> lines) {
    final List<SentryStackFrame> frames = new ArrayList<>();
    SentryStackFrame lastJavaFrame = null;

    final Matcher nativeRe = NATIVE_RE.matcher("");
    final Matcher nativeNoLocRe = NATIVE_NO_LOC_RE.matcher("");
    final Matcher javaRe = JAVA_RE.matcher("");
    final Matcher jniRe = JNI_RE.matcher("");
    final Matcher lockedRe = LOCKED_RE.matcher("");
    final Matcher waitingOnRe = WAITING_ON_RE.matcher("");
    final Matcher sleepingOnRe = SLEEPING_ON_RE.matcher("");
    final Matcher waitingToLockHeldRe = WAITING_TO_LOCK_HELD_RE.matcher("");
    final Matcher waitingToLockRe = WAITING_TO_LOCK_RE.matcher("");
    final Matcher waitingToLockUnknownRe = WAITING_TO_LOCK_UNKNOWN_RE.matcher("");
    final Matcher noManagedStackFrameRe = NO_MANAGED_STACK_FRAME_RE.matcher("");
    final Matcher blankRe = BLANK_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      final String text = line.text;
      if (matches(nativeRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setPackage(nativeRe.group(1));
        frame.setSymbol(nativeRe.group(2));
        frame.setLineno(getInteger(nativeRe, 3, null));
        frames.add(frame);
        lastJavaFrame = null;
      } else if (matches(nativeNoLocRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setPackage(nativeNoLocRe.group(1));
        frame.setSymbol(nativeNoLocRe.group(2));
        frames.add(frame);
        lastJavaFrame = null;
      } else if (matches(javaRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        final String packageName = javaRe.group(1);
        final String className = javaRe.group(2);
        final String module = String.format("%s.%s", packageName, className);
        frame.setModule(module);
        frame.setFunction(javaRe.group(3));
        frame.setFilename(javaRe.group(4));
        frame.setLineno(getUInteger(javaRe, 5, null));
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(jniRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        final String packageName = jniRe.group(1);
        final String className = jniRe.group(2);
        final String module = String.format("%s.%s", packageName, className);
        frame.setModule(module);
        frame.setFunction(jniRe.group(3));
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(lockedRe, text)
        || matches(waitingOnRe, text)
        || matches(sleepingOnRe, text)
        || matches(waitingToLockHeldRe, text)
        || matches(waitingToLockRe, text)
        || matches(waitingToLockUnknownRe, text)) {
      } else if (text.length() == 0 || matches(blankRe, text)) {
        break;
      }
    }

    final SentryStackTrace stackTrace = new SentryStackTrace(frames);
    // it's a thread dump
    stackTrace.setSnapshot(true);
    return stackTrace;
  }

  @NotNull
  private void parseAndAssignLockReason(final @NotNull SentryStackFrame lastJavaFrame) {
    //lastJavaFrame.
  }

  private boolean matches(Matcher matcher, String text) {
    matcher.reset(text);
    return matcher.matches();
  }

  @Nullable
  private Long getLong(final @NotNull Matcher matcher, final int group,
    final @Nullable Long defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Long.parseLong(str);
    }
  }

  @Nullable
  private Integer getInteger(final @NotNull Matcher matcher, final int group,
    final @Nullable Integer defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Integer.parseInt(str);
    }
  }

  @Nullable
  private Integer getUInteger(final @NotNull Matcher matcher, final int group,
    final @Nullable Integer defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      final Integer parsed = Integer.parseInt(str);
      return parsed >= 0 ? parsed : defaultValue;
    }
  }
}

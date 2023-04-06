package io.sentry.android.core.internal.threaddump;

import java.util.regex.Pattern;

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


}

package io.sentry.android.core.internal.tombstone;

import androidx.annotation.NonNull;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.NativeEventUtils;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class TombstoneParser implements Closeable {

  private final InputStream tombstoneStream;
  @NotNull private final List<String> inAppIncludes;
  @NotNull private final List<String> inAppExcludes;
  // TODO: in theory can be null, but practically not for native crashes
  private final String nativeLibraryDir;
  private final Map<String, String> excTypeValueMap = new HashMap<>();

  private static String formatHex(long value) {
    return String.format("0x%x", value);
  }

  public TombstoneParser(
      @NonNull final InputStream tombstoneStream,
      @NotNull List<String> inAppIncludes,
      @NotNull List<String> inAppExcludes,
      String nativeLibraryDir) {
    this.tombstoneStream = tombstoneStream;
    this.inAppIncludes = inAppIncludes;
    this.inAppExcludes = inAppExcludes;
    this.nativeLibraryDir = nativeLibraryDir;

    // keep the current signal type -> value mapping for compatibility
    excTypeValueMap.put("SIGILL", "IllegalInstruction");
    excTypeValueMap.put("SIGTRAP", "Trap");
    excTypeValueMap.put("SIGABRT", "Abort");
    excTypeValueMap.put("SIGBUS", "BusError");
    excTypeValueMap.put("SIGFPE", "FloatingPointException");
    excTypeValueMap.put("SIGSEGV", "Segfault");
  }

  @NonNull
  public SentryEvent parse() throws IOException {
    @NonNull
    final TombstoneProtos.Tombstone tombstone =
        TombstoneProtos.Tombstone.parseFrom(tombstoneStream);

    final SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.FATAL);

    // must use the "native" platform because otherwise the stack-trace wouldn't be correctly parsed
    event.setPlatform("native");

    event.setMessage(constructMessage(tombstone));
    event.setDebugMeta(createDebugMeta(tombstone));
    event.setExceptions(createException(tombstone));
    event.setThreads(
        createThreads(tombstone, Objects.requireNonNull(event.getExceptions()).get(0)));

    return event;
  }

  @NonNull
  private List<SentryThread> createThreads(
      @NonNull final TombstoneProtos.Tombstone tombstone, @NonNull final SentryException exc) {
    final List<SentryThread> threads = new ArrayList<>();
    for (Map.Entry<Integer, TombstoneProtos.Thread> threadEntry :
        tombstone.getThreadsMap().entrySet()) {
      final TombstoneProtos.Thread threadEntryValue = threadEntry.getValue();

      final SentryThread thread = new SentryThread();
      thread.setId(Long.valueOf(threadEntry.getKey()));
      thread.setName(threadEntryValue.getName());

      final SentryStackTrace stacktrace = createStackTrace(threadEntryValue);
      thread.setStacktrace(stacktrace);
      if (tombstone.getTid() == threadEntryValue.getId()) {
        thread.setCrashed(true);
        // even though we refer to the thread_id from the exception,
        // the backend currently requires a stack-trace in exception
        exc.setStacktrace(stacktrace);
      }
      threads.add(thread);
    }

    return threads;
  }

  @NonNull
  private SentryStackTrace createStackTrace(@NonNull final TombstoneProtos.Thread thread) {
    final List<SentryStackFrame> frames = new ArrayList<>();

    for (TombstoneProtos.BacktraceFrame frame : thread.getCurrentBacktraceList()) {
      if (frame.getFileName().endsWith("libart.so")) {
        // We ignore all ART frames for time being because they aren't actionable for app developers
        continue;
      }
      final SentryStackFrame stackFrame = new SentryStackFrame();
      stackFrame.setPackage(frame.getFileName());
      stackFrame.setFunction(frame.getFunctionName());
      stackFrame.setInstructionAddr(formatHex(frame.getPc()));

      // TODO: is this the right order?
      boolean inApp = false;
      for (String inclusion : this.inAppIncludes) {
        if (frame.getFunctionName().startsWith(inclusion)) {
          inApp = true;
          break;
        }
      }

      for (String exclusion : this.inAppExcludes) {
        if (frame.getFunctionName().startsWith(exclusion)) {
          inApp = false;
          break;
        }
      }

      inApp = inApp || frame.getFileName().startsWith(this.nativeLibraryDir);

      stackFrame.setInApp(inApp);
      frames.add(0, stackFrame);
    }

    final SentryStackTrace stacktrace = new SentryStackTrace();
    stacktrace.setFrames(frames);

    // `libunwindstack` used for tombstones already applies instruction address adjustment:
    // https://android.googlesource.com/platform/system/unwinding/+/refs/heads/main/libunwindstack/Regs.cpp#175
    // prevent "processing" from doing it again.
    stacktrace.setInstructionAddressAdjustment(SentryStackTrace.InstructionAddressAdjustment.NONE);

    final Map<String, String> registers = new HashMap<>();
    for (TombstoneProtos.Register register : thread.getRegistersList()) {
      registers.put(register.getName(), formatHex(register.getU64()));
    }
    stacktrace.setRegisters(registers);

    return stacktrace;
  }

  @NonNull
  private List<SentryException> createException(@NonNull TombstoneProtos.Tombstone tombstone) {
    final SentryException exception = new SentryException();

    if (tombstone.hasSignalInfo()) {
      final TombstoneProtos.Signal signalInfo = tombstone.getSignalInfo();
      exception.setType(signalInfo.getName());
      exception.setValue(excTypeValueMap.get(signalInfo.getName()));
      exception.setMechanism(createMechanismFromSignalInfo(signalInfo));
    }

    exception.setThreadId((long) tombstone.getTid());
    final List<SentryException> exceptions = new ArrayList<>(1);
    exceptions.add(exception);

    return exceptions;
  }

  @NonNull
  private static Mechanism createMechanismFromSignalInfo(
      @NonNull final TombstoneProtos.Signal signalInfo) {

    final Mechanism mechanism = new Mechanism();
    mechanism.setType(NativeExceptionMechanism.TOMBSTONE.getValue());
    mechanism.setHandled(false);
    mechanism.setSynthetic(true);

    final Map<String, Object> meta = new HashMap<>();
    meta.put("number", signalInfo.getNumber());
    meta.put("name", signalInfo.getName());
    meta.put("code", signalInfo.getCode());
    meta.put("code_name", signalInfo.getCodeName());
    mechanism.setMeta(meta);

    return mechanism;
  }

  @NonNull
  private Message constructMessage(@NonNull final TombstoneProtos.Tombstone tombstone) {
    final Message message = new Message();
    final TombstoneProtos.Signal signalInfo = tombstone.getSignalInfo();

    // reproduce the message `debuggerd` would use to dump the stack trace in logcat
    String command = String.join(" ", tombstone.getCommandLineList());
    if (tombstone.hasSignalInfo()) {
      String abortMessage = tombstone.getAbortMessage();
      message.setFormatted(
          String.format(
              Locale.ROOT,
              "%sFatal signal %s (%d), %s (%d), pid = %d (%s)",
              !abortMessage.isEmpty() ? abortMessage + ": " : "",
              signalInfo.getName(),
              signalInfo.getNumber(),
              signalInfo.getCodeName(),
              signalInfo.getCode(),
              tombstone.getPid(),
              command));
    } else {
      message.setFormatted(
          String.format(Locale.ROOT, "Fatal exit pid = %d (%s)", tombstone.getPid(), command));
    }

    return message;
  }

  private DebugMeta createDebugMeta(@NonNull final TombstoneProtos.Tombstone tombstone) {
    final List<DebugImage> images = new ArrayList<>();

    for (TombstoneProtos.MemoryMapping module : tombstone.getMemoryMappingsList()) {
      // exclude anonymous and non-executable maps
      if (module.getBuildId().isEmpty()
          || module.getMappingName().isEmpty()
          || !module.getExecute()) {
        continue;
      }
      final DebugImage image = new DebugImage();
      final String codeId = module.getBuildId();
      image.setCodeId(codeId);
      image.setCodeFile(module.getMappingName());

      final String debugId = NativeEventUtils.buildIdToDebugId(codeId);
      image.setDebugId(debugId != null ? debugId : codeId);

      image.setImageAddr(formatHex(module.getBeginAddress()));
      image.setImageSize(module.getEndAddress() - module.getBeginAddress());
      image.setType("elf");

      images.add(image);
    }

    final DebugMeta debugMeta = new DebugMeta();
    debugMeta.setImages(images);

    return debugMeta;
  }

  @Override
  public void close() throws IOException {
    tombstoneStream.close();
  }
}

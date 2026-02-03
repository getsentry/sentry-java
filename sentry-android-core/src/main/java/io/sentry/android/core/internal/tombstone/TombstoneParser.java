package io.sentry.android.core.internal.tombstone;

import androidx.annotation.NonNull;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryStackTraceFactory;
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
import org.jetbrains.annotations.Nullable;

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

      @Nullable
      Boolean inApp =
          SentryStackTraceFactory.isInApp(frame.getFunctionName(), inAppIncludes, inAppExcludes);

      inApp = (inApp != null && inApp) || frame.getFileName().startsWith(this.nativeLibraryDir);

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

  /**
   * Helper class to accumulate memory mappings into a single module. Modules in the Sentry sense
   * are the entire readable memory map for a file, not just the executable segment. This is
   * important to maintain the file-offset contract of map entries, which is necessary to resolve
   * runtime instruction addresses in the files uploaded for symbolication.
   */
  private static class ModuleAccumulator {
    String mappingName;
    String buildId;
    long beginAddress;
    long endAddress;

    ModuleAccumulator(TombstoneProtos.MemoryMapping mapping) {
      this.mappingName = mapping.getMappingName();
      this.buildId = mapping.getBuildId();
      this.beginAddress = mapping.getBeginAddress();
      this.endAddress = mapping.getEndAddress();
    }

    void extendTo(long newEndAddress) {
      this.endAddress = newEndAddress;
    }

    DebugImage toDebugImage() {
      if (buildId.isEmpty()) {
        return null;
      }
      final DebugImage image = new DebugImage();
      image.setCodeId(buildId);
      image.setCodeFile(mappingName);

      final String debugId = NativeEventUtils.buildIdToDebugId(buildId);
      image.setDebugId(debugId != null ? debugId : buildId);

      image.setImageAddr(formatHex(beginAddress));
      image.setImageSize(endAddress - beginAddress);
      image.setType("elf");

      return image;
    }
  }

  private DebugMeta createDebugMeta(@NonNull final TombstoneProtos.Tombstone tombstone) {
    final List<DebugImage> images = new ArrayList<>();

    // Coalesce memory mappings into modules similar to how sentry-native does it.
    // A module consists of all readable mappings for the same file, starting from
    // the first mapping that has a valid ELF header (indicated by offset 0 with build_id).
    // In sentry-native, is_valid_elf_header() reads the ELF magic bytes from memory,
    // which is only present at the start of the file (offset 0). We use offset == 0
    // combined with non-empty build_id as a proxy for this check.
    ModuleAccumulator currentModule = null;

    for (TombstoneProtos.MemoryMapping mapping : tombstone.getMemoryMappingsList()) {
      // Skip mappings that are not readable
      if (!mapping.getRead()) {
        continue;
      }

      // Skip mappings with empty name or in /dev/
      final String mappingName = mapping.getMappingName();
      if (mappingName.isEmpty() || mappingName.startsWith("/dev/")) {
        continue;
      }

      final boolean hasBuildId = !mapping.getBuildId().isEmpty();
      final boolean isFileStart = mapping.getOffset() == 0;

      if (hasBuildId && isFileStart) {
        // Check for duplicated mappings: On Android, the same ELF can have multiple
        // mappings at offset 0 with different permissions (r--p, r-xp, r--p).
        // If it's the same file as the current module, just extend it.
        if (currentModule != null && mappingName.equals(currentModule.mappingName)) {
          currentModule.extendTo(mapping.getEndAddress());
          continue;
        }

        // Flush the previous module (different file)
        if (currentModule != null) {
          final DebugImage image = currentModule.toDebugImage();
          if (image != null) {
            images.add(image);
          }
        }

        // Start a new module
        currentModule = new ModuleAccumulator(mapping);
      } else if (currentModule != null && mappingName.equals(currentModule.mappingName)) {
        // Extend the current module with this mapping (same file, continuation)
        currentModule.extendTo(mapping.getEndAddress());
      }
    }

    // Flush the last module
    if (currentModule != null) {
      final DebugImage image = currentModule.toDebugImage();
      if (image != null) {
        images.add(image);
      }
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

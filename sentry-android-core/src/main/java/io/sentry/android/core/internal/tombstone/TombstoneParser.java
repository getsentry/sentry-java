package io.sentry.android.core.internal.tombstone;

import androidx.annotation.NonNull;
import com.abovevacant.epitaph.core.BacktraceFrame;
import com.abovevacant.epitaph.core.MemoryMapping;
import com.abovevacant.epitaph.core.Register;
import com.abovevacant.epitaph.core.Signal;
import com.abovevacant.epitaph.core.Tombstone;
import com.abovevacant.epitaph.core.TombstoneThread;
import com.abovevacant.epitaph.wire.TombstoneDecoder;
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

  @Nullable private final InputStream tombstoneStream;
  @NotNull private final List<String> inAppIncludes;
  @NotNull private final List<String> inAppExcludes;
  @Nullable private final String nativeLibraryDir;
  private final Map<String, String> excTypeValueMap = new HashMap<>();

  private static String formatHex(long value) {
    return String.format("0x%x", value);
  }

  public TombstoneParser(
      @NotNull List<String> inAppIncludes,
      @NotNull List<String> inAppExcludes,
      @Nullable String nativeLibraryDir) {
    this(null, inAppIncludes, inAppExcludes, nativeLibraryDir);
  }

  public TombstoneParser(
      @Nullable final InputStream tombstoneStream,
      @NotNull List<String> inAppIncludes,
      @NotNull List<String> inAppExcludes,
      @Nullable String nativeLibraryDir) {
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
    if (tombstoneStream == null) {
      throw new IOException("No InputStream provided; use parse(Tombstone) instead.");
    }
    return parse(TombstoneDecoder.decode(tombstoneStream));
  }

  @NonNull
  public SentryEvent parse(@NonNull final Tombstone tombstone) {
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
      @NonNull final Tombstone tombstone, @NonNull final SentryException exc) {
    final List<SentryThread> threads = new ArrayList<>();
    for (Map.Entry<Integer, TombstoneThread> threadEntry : tombstone.threads.entrySet()) {
      final TombstoneThread threadEntryValue = threadEntry.getValue();

      final SentryThread thread = new SentryThread();
      thread.setId(Long.valueOf(threadEntry.getKey()));
      thread.setName(threadEntryValue.name);

      final SentryStackTrace stacktrace = createStackTrace(threadEntryValue);
      thread.setStacktrace(stacktrace);
      if (tombstone.tid == threadEntryValue.id) {
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
  private SentryStackTrace createStackTrace(@NonNull final TombstoneThread thread) {
    final List<SentryStackFrame> frames = new ArrayList<>();

    for (BacktraceFrame frame : thread.backtrace) {
      if (frame.fileName.endsWith("libart.so")) {
        // We ignore all ART frames for time being because they aren't actionable for app developers
        continue;
      }
      if (frame.fileName.startsWith("<anonymous") && frame.functionName.isEmpty()) {
        // Code in anonymous VMAs that does not resolve to a function name, cannot be symbolicated
        // in the backend either, and thus has no value in the UI.
        continue;
      }
      final SentryStackFrame stackFrame = new SentryStackFrame();
      stackFrame.setPackage(frame.fileName);
      stackFrame.setFunction(frame.functionName);
      stackFrame.setInstructionAddr(formatHex(frame.pc));

      // inAppIncludes/inAppExcludes filter by Java/Kotlin package names, which don't overlap
      // with native C/C++ function names (e.g., "crash", "__libc_init"). For native frames,
      // isInApp() returns null, making nativeLibraryDir the effective in-app check.
      // epitaph returns "" for unset function names, which would incorrectly return true
      // from isInApp(), so we treat empty as false to let nativeLibraryDir decide.
      final String functionName = frame.functionName;
      @Nullable
      Boolean inApp =
          functionName.isEmpty()
              ? Boolean.FALSE
              : SentryStackTraceFactory.isInApp(functionName, inAppIncludes, inAppExcludes);

      final boolean isInNativeLibraryDir =
          nativeLibraryDir != null && frame.fileName.startsWith(nativeLibraryDir);
      inApp = (inApp != null && inApp) || isInNativeLibraryDir;

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
    for (Register register : thread.registers) {
      registers.put(register.name, formatHex(register.value));
    }
    stacktrace.setRegisters(registers);

    return stacktrace;
  }

  @NonNull
  private List<SentryException> createException(@NonNull Tombstone tombstone) {
    final SentryException exception = new SentryException();

    if (tombstone.hasSignal()) {
      final Signal signalInfo = tombstone.signal;
      exception.setType(signalInfo.name);
      exception.setValue(excTypeValueMap.get(signalInfo.name));
      exception.setMechanism(createMechanismFromSignalInfo(signalInfo));
    }

    exception.setThreadId((long) tombstone.tid);
    final List<SentryException> exceptions = new ArrayList<>(1);
    exceptions.add(exception);

    return exceptions;
  }

  @NonNull
  private static Mechanism createMechanismFromSignalInfo(@NonNull final Signal signalInfo) {

    final Mechanism mechanism = new Mechanism();
    mechanism.setType(NativeExceptionMechanism.TOMBSTONE.getValue());
    mechanism.setHandled(false);
    mechanism.setSynthetic(true);

    final Map<String, Object> meta = new HashMap<>();
    meta.put("number", signalInfo.number);
    meta.put("name", signalInfo.name);
    meta.put("code", signalInfo.code);
    meta.put("code_name", signalInfo.codeName);
    mechanism.setMeta(meta);

    return mechanism;
  }

  @NonNull
  private Message constructMessage(@NonNull final Tombstone tombstone) {
    final Message message = new Message();
    final Signal signalInfo = tombstone.signal;

    // reproduce the message `debuggerd` would use to dump the stack trace in logcat
    String command = String.join(" ", tombstone.commandLine);
    if (tombstone.hasSignal()) {
      String abortMessage = tombstone.abortMessage;
      message.setFormatted(
          String.format(
              Locale.ROOT,
              "%sFatal signal %s (%d), %s (%d), pid = %d (%s)",
              !abortMessage.isEmpty() ? abortMessage + ": " : "",
              signalInfo.name,
              signalInfo.number,
              signalInfo.codeName,
              signalInfo.code,
              tombstone.pid,
              command));
    } else {
      message.setFormatted(
          String.format(Locale.ROOT, "Fatal exit pid = %d (%s)", tombstone.pid, command));
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

    ModuleAccumulator(MemoryMapping mapping) {
      this.mappingName = mapping.mappingName;
      this.buildId = mapping.buildId;
      this.beginAddress = mapping.beginAddress;
      this.endAddress = mapping.endAddress;
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

  private DebugMeta createDebugMeta(@NonNull final Tombstone tombstone) {
    final List<DebugImage> images = new ArrayList<>();

    // Coalesce memory mappings into modules similar to how sentry-native does it.
    // A module consists of all readable mappings for the same file, starting from
    // the first mapping that has a valid ELF header (indicated by offset 0 with build_id).
    // In sentry-native, is_valid_elf_header() reads the ELF magic bytes from memory,
    // which is only present at the start of the file (offset 0). We use offset == 0
    // combined with non-empty build_id as a proxy for this check.
    ModuleAccumulator currentModule = null;

    for (MemoryMapping mapping : tombstone.memoryMappings) {
      // Skip mappings that are not readable
      if (!mapping.read) {
        continue;
      }

      // Skip mappings with empty name or in /dev/
      final String mappingName = mapping.mappingName;
      if (mappingName.isEmpty() || mappingName.startsWith("/dev/")) {
        continue;
      }

      final boolean hasBuildId = !mapping.buildId.isEmpty();
      final boolean isFileStart = mapping.offset == 0;

      if (hasBuildId && isFileStart) {
        // Check for duplicated mappings: On Android, the same ELF can have multiple
        // mappings at offset 0 with different permissions (r--p, r-xp, r--p).
        // If it's the same file as the current module, just extend it.
        if (currentModule != null && mappingName.equals(currentModule.mappingName)) {
          currentModule.extendTo(mapping.endAddress);
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
        currentModule.extendTo(mapping.endAddress);
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

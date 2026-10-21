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

      // thread id always equals the process id,
      // so we use it to reliably detect the main thread
      if (tombstone.pid == threadEntryValue.id) {
        // the OS may provide a (truncated) process name; normalize it
        // back to "main" so downstream consumers see a consistent name
        thread.setName("main");
        thread.setMain(true);
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
      if (!frame.buildId.isEmpty() && frame.pc >= frame.relPc) {
        // libunwindstack has already resolved rel_pc against the embedded or standalone ELF.
        // The containing file offset (for example, the offset inside an APK) is irrelevant to the
        // ELF's runtime image address.
        stackFrame.setImageAddr(formatHex(frame.pc - frame.relPc));
      }

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
    private static final long PAGE_SIZE_4KIB = 4096;
    private static final long PAGE_SIZE_16KIB = 16384;

    String mappingName;
    String buildId;
    long beginAddress;
    long endAddress;
    long previousBeginAddress;
    long previousEndAddress;
    long previousOffset;

    ModuleAccumulator(final @NotNull MemoryMapping mapping) {
      this.mappingName = mapping.mappingName;
      this.buildId = mapping.buildId;
      this.beginAddress = mapping.beginAddress;
      this.endAddress = mapping.endAddress;
      this.previousBeginAddress = mapping.beginAddress;
      this.previousEndAddress = mapping.endAddress;
      this.previousOffset = mapping.offset;
    }

    boolean isSameModule(final @NotNull MemoryMapping mapping) {
      return mappingName.equals(mapping.mappingName) && buildId.equals(mapping.buildId);
    }

    boolean canExtendTo(final @NotNull MemoryMapping mapping, final long pageSize) {

      if (!mappingName.equals(mapping.mappingName)
          || mapping.beginAddress < previousEndAddress
          || mapping.offset < previousOffset) {
        return false;
      }

      // PT_LOAD virtual-address and file-offset gaps can differ by one segment-alignment unit.
      // Compare adjacent mappings so this difference does not accumulate across the module.
      final long previousSize = previousEndAddress - previousBeginAddress;
      final long addressGap = mapping.beginAddress - previousEndAddress;
      final long fileOffsetGap = mapping.offset - (previousOffset + previousSize);
      final long delta = addressGap - fileOffsetGap;

      // Android ELFs built for 16 KiB pages can also run on 4 KiB devices, so the ELF alignment
      // can be larger than the tombstone's runtime page size.
      final long runtimePageSize = pageSize > 0 ? pageSize : PAGE_SIZE_4KIB;
      final long alignmentTolerance = Math.max(runtimePageSize, PAGE_SIZE_16KIB);
      return delta >= -alignmentTolerance && delta <= alignmentTolerance;
    }

    void extendTo(final @NotNull MemoryMapping mapping) {
      this.endAddress = Math.max(endAddress, mapping.endAddress);
      this.previousBeginAddress = mapping.beginAddress;
      this.previousEndAddress = mapping.endAddress;
      this.previousOffset = mapping.offset;
    }

    DebugImage toDebugImage() {
      if (buildId.isEmpty()) {
        return null;
      }
      final DebugImage image = new DebugImage();
      image.setCodeId(buildId);
      image.setCodeFile(mappingName);

      final @Nullable String debugId = NativeEventUtils.buildIdToDebugId(buildId);
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
    // Android's libunwindstack has already parsed each ELF and records its build ID in the
    // tombstone. An ELF stored uncompressed inside an APK starts at a non-zero container offset,
    // so the mapping offset cannot be used to validate whether the mapping starts an ELF.
    @Nullable ModuleAccumulator currentModule = null;

    for (final @NotNull MemoryMapping mapping : tombstone.memoryMappings) {
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

      if (hasBuildId) {
        // The same ELF can have multiple mappings with its build ID. APK-embedded ELFs all share
        // the APK mapping name, so the build ID is also required to distinguish their modules.
        if (currentModule != null && currentModule.isSameModule(mapping)) {
          currentModule.extendTo(mapping);
          continue;
        }

        // Flush the previous module (different ELF)
        if (currentModule != null) {
          final DebugImage image = currentModule.toDebugImage();
          if (image != null) {
            images.add(image);
          }
        }

        // Start a new module
        currentModule = new ModuleAccumulator(mapping);
      } else if (currentModule != null && currentModule.canExtendTo(mapping, tombstone.pageSize)) {
        // Extend the current module with this mapping (same ELF, continuation).
        currentModule.extendTo(mapping);
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
    if (tombstoneStream != null) {
      tombstoneStream.close();
    }
  }
}

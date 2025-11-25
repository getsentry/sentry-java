package io.sentry.android.core.internal.tombstone;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;

public class TombstoneParser {

  private final InputStream tombstoneStream;
  private final Map<String, String> excTypeValueMap = new HashMap<>();

  public TombstoneParser(InputStream tombstoneStream) {
    this.tombstoneStream = tombstoneStream;

    // keep the current signal type -> value mapping for compatibility
    excTypeValueMap.put("SIGILL", "IllegalInstruction");
    excTypeValueMap.put("SIGTRAP", "Trap");
    excTypeValueMap.put("SIGABRT", "Abort");
    excTypeValueMap.put("SIGBUS", "BusError");
    excTypeValueMap.put("SIGFPE", "FloatingPointException");
    excTypeValueMap.put("SIGSEGV", "Segfault");
  }

  public SentryEvent parse() throws IOException {
    TombstoneProtos.Tombstone tombstone = TombstoneProtos.Tombstone.parseFrom(tombstoneStream);

    SentryEvent event = new SentryEvent();
    event.setLevel(SentryLevel.FATAL);

    // we must use the "native" platform because otherwise the stack-trace would not be correctly parsed
    event.setPlatform("native");

    event.setMessage(constructMessage(tombstone));
    event.setDebugMeta(createDebugMeta(tombstone));
    event.setExceptions(createException(tombstone));
    assert event.getExceptions() != null;
    event.setThreads(createThreads(tombstone, event.getExceptions().get(0)));

    return event;
  }

  @NonNull
  private List<SentryThread> createThreads(TombstoneProtos.Tombstone tombstone, SentryException exc) {
    List<SentryThread> threads = new ArrayList<>();
    for (Map.Entry<Integer, TombstoneProtos.Thread> threadEntry :
      tombstone.getThreadsMap().entrySet()) {

      SentryThread thread = new SentryThread();
      thread.setId(Long.valueOf(threadEntry.getKey()));
      thread.setName(threadEntry.getValue().getName());

      SentryStackTrace stacktrace = createStackTrace(threadEntry);
      thread.setStacktrace(stacktrace);
      if (tombstone.getTid() == threadEntry.getValue().getId()) {
        thread.setCrashed(true);
        // even though we refer to the thread_id from the exception, the backend currently requires a stack-trace in exception
        exc.setStacktrace(stacktrace);
      }
      threads.add(thread);
    }

    return threads;
  }

  @NonNull
  private static SentryStackTrace createStackTrace(Map.Entry<Integer, TombstoneProtos.Thread> threadEntry) {
    List<SentryStackFrame> frames = new ArrayList<>();

    for (TombstoneProtos.BacktraceFrame frame :
      threadEntry.getValue().getCurrentBacktraceList()) {
      SentryStackFrame stackFrame = new SentryStackFrame();
      stackFrame.setPackage(frame.getFileName());
      stackFrame.setFunction(frame.getFunctionName());
      stackFrame.setInstructionAddr(String.format("0x%x", frame.getPc()));
      frames.add(0, stackFrame);
    }

    SentryStackTrace stacktrace = new SentryStackTrace();
    stacktrace.setFrames(frames);

    Map<String, Object> unknown = new HashMap<>();
    // `libunwindstack` used for tombstone generation already applies instruction address adjustment:
    // https://android.googlesource.com/platform/system/unwinding/+/refs/heads/main/libunwindstack/Regs.cpp#175
    // prevent "processing" from doing it again.
    unknown.put("instruction_addr_adjustment", "none");
    stacktrace.setUnknown(unknown);

    Map<String, String> registers = new HashMap<>();
    for (TombstoneProtos.Register register : threadEntry.getValue().getRegistersList()) {
      registers.put(register.getName(), String.format("0x%x", register.getU64()));
    }
    stacktrace.setRegisters(registers);

    return stacktrace;
  }

  @NonNull
  private List<SentryException> createException(TombstoneProtos.Tombstone tombstone) {
    TombstoneProtos.Signal signalInfo = tombstone.getSignalInfo();

    SentryException exception = new SentryException();
    exception.setType(signalInfo.getName());
    exception.setValue(excTypeValueMap.get(signalInfo.getName()));
    exception.setMechanism(createMechanismFromSignalInfo(signalInfo));
    exception.setThreadId((long) tombstone.getTid());

    List<SentryException> exceptions = new ArrayList<>();
    exceptions.add(exception);

    return exceptions;
  }

  @NonNull
  private static Mechanism createMechanismFromSignalInfo(TombstoneProtos.Signal signalInfo) {
    Map<String, Object> meta = new HashMap<>();
    meta.put("number", signalInfo.getNumber());
    meta.put("name", signalInfo.getName());
    meta.put("code", signalInfo.getCode());
    meta.put("code_name", signalInfo.getCodeName());

    Mechanism mechanism = new Mechanism();
    // this follows the current processing triggers strictly, changing any of these alters grouping and name (long-term we might want to
    // have a tombstone mechanism)
    mechanism.setType("signalhandler");
    mechanism.setHandled(false);
    mechanism.setSynthetic(true);
    mechanism.setMeta(meta);

    return mechanism;
  }

  @NonNull
  private Message constructMessage(TombstoneProtos.Tombstone tombstone) {
    Message message = new Message();
    TombstoneProtos.Signal signalInfo = tombstone.getSignalInfo();

    // reproduce the message `debuggerd` would use to dump the stack trace in logcat
    message.setFormatted(
      String.format(Locale.getDefault(),
        "Fatal signal %s (%d), %s (%d), pid = %d (%s)",
        signalInfo.getName(),
        signalInfo.getNumber(),
        signalInfo.getCodeName(),
        signalInfo.getCode(),
        tombstone.getPid(),
        String.join(" ", tombstone.getCommandLineList())));

    return message;
  }

  private DebugMeta createDebugMeta(TombstoneProtos.Tombstone tombstone) {
    List<DebugImage> images = new ArrayList<>();

    for (TombstoneProtos.MemoryMapping module : tombstone.getMemoryMappingsList()) {
      // exclude anonymous and non-executable maps
      if (module.getBuildId().isEmpty()
        || module.getMappingName().isEmpty()
        || !module.getExecute()) {
        continue;
      }
      DebugImage image = new DebugImage();
      image.setCodeId(module.getBuildId());
      image.setCodeFile(module.getMappingName());
      image.setDebugId(module.getBuildId());
      image.setImageAddr(String.format("0x%x", module.getBeginAddress()));
      image.setImageSize(module.getEndAddress() - module.getBeginAddress());
      image.setType("elf");

      images.add(image);
    }

    DebugMeta debugMeta = new DebugMeta();
    debugMeta.setImages(images);

    return debugMeta;
  }
}

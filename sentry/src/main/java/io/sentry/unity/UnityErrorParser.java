package io.sentry.unity;

import io.sentry.Sentry;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnityErrorParser {
  private static final Pattern BEGIN_NATIVE_SIGNAL_RE =
    Pattern.compile("signal (\\d+) \\((.+?)\\)(?:, code (-?\\d+) \\((.+?)\\))?(?:, fault addr (.+))?");
  private static final Pattern CAUSE_RE = Pattern.compile("Cause: (.+)");
  private static final Pattern REGISTER_RE = Pattern.compile("([a-zA-Z]+\\d*)\\s+([0-9a-f]+)");
  private static final Pattern NATIVE_FRAME_RE = Pattern.compile("\\s*#\\d+ \\S+ ([0-9a-fA-F]+)\\s+(.*?)\\s*\\((.*)\\+(\\d+)\\)(?: \\(.*\\))?");
  private static final Pattern NATIVE_FRAME_NO_LINE_RE = Pattern.compile("\\s*#\\d+ \\S+ ([0-9a-fA-F]+)\\s+(.*?)\\s*\\((.*)\\)( \\(.*\\))");
  private static final Pattern NATIVE_FRAME_NO_FUN_RE = Pattern.compile("\\s*#\\d+ \\S+ ([0-9a-fA-F]+)\\s+([^\\s]+)\\s*\\(?(.*)\\)?");

  public UnityErrorParser() {
  }

  @NotNull
  public SentryException parse(final @NotNull SentryException exception, final @NotNull Lines lines) {
    final Matcher beginNativeSignalRe = BEGIN_NATIVE_SIGNAL_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        //options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
        return exception;
      }

      final String text = line.text;
      if (matches(beginNativeSignalRe, text)) {
        final String signal = beginNativeSignalRe.group(2);
        exception.setType(signal);
        final Map<String, Object> meta = new HashMap<>(1);
        final Map<String, Object> signalMap = new HashMap<>(2);
        signalMap.put("name", signal);
        signalMap.put("number", getInteger(beginNativeSignalRe, 1, null));
        meta.put("signal", signalMap);

        final @Nullable Mechanism mechanism = exception.getMechanism();
        if (mechanism != null) {
          mechanism.setMeta(meta);
        }

        parseRegisters(lines, exception);
      }
    }
    return exception;
  }

  private void parseRegisters(final @NotNull Lines lines, final @NotNull SentryException exception) {
    final Matcher causeRe = CAUSE_RE.matcher("");
    final Matcher registerRe = REGISTER_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        //options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
        break;
      }
      final String text = line.text;
      if (matches(causeRe, text)) {
        exception.setValue(causeRe.group(1));
      } else if (finds(registerRe, text)) {
        lines.rewind();

        final SentryStackTrace stackTrace = parseStacktrace(lines);
        exception.setStacktrace(stackTrace);
      }
    }
  }

  @NotNull
  private SentryStackTrace parseStacktrace(final @NotNull Lines lines) {
    final List<SentryStackFrame> frames = new ArrayList<>();
    final Map<String, String> registers = new HashMap<>();

    final Matcher registerRe = REGISTER_RE.matcher("");
    final Matcher nativeFrameRe = NATIVE_FRAME_RE.matcher("");
    final Matcher nativeFrameNoLocRe = NATIVE_FRAME_NO_LINE_RE.matcher("");
    final Matcher nativeFrameNoFunRe = NATIVE_FRAME_NO_FUN_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        //options.getLogger().log(SentryLevel.WARNING, "Internal error while parsing thread dump.");
        break;
      }
      final String text = line.text;
      if (matches(nativeFrameRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setInstructionAddr(convertToHex(nativeFrameRe.group(1)));
        frame.setPackage(nativeFrameRe.group(2));
        frame.setFunction(nativeFrameRe.group(3));
        frame.setLineno(getInteger(nativeFrameRe, 4, null));
        frames.add(frame);
      } else if (matches(nativeFrameNoLocRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setInstructionAddr(convertToHex(nativeFrameNoLocRe.group(1)));
        frame.setPackage(nativeFrameNoLocRe.group(2));
        frame.setFunction(nativeFrameNoLocRe.group(3));
        frames.add(frame);
      } else if (matches(nativeFrameNoFunRe, text)) {
        final SentryStackFrame frame = new SentryStackFrame();
        frame.setInstructionAddr(convertToHex(nativeFrameNoFunRe.group(1)));
        frame.setPackage(nativeFrameNoFunRe.group(2));
        frames.add(frame);
      } else if (finds(registerRe, text)) {
        do {
          registers.put(registerRe.group(1), convertToHex(registerRe.group(2)));
        } while (registerRe.find());
      }
    }

    // Sentry expects frames to be in reverse order
    Collections.reverse(frames);
    final SentryStackTrace stackTrace = new SentryStackTrace(frames);
    stackTrace.setRegisters(registers);
    return stackTrace;
  }

  private String convertToHex(final String value) {
    BigInteger registerValue = new BigInteger(value, 16);
    return "0x" + registerValue.toString(16);
  }

  private boolean matches(final @NotNull Matcher matcher, final @NotNull String text) {
    matcher.reset(text);
    return matcher.matches();
  }

  private boolean finds(final @NotNull Matcher matcher, final @NotNull String text) {
    matcher.reset(text);
    return matcher.find();
  }

  @Nullable
  private Integer getInteger(
    final @NotNull Matcher matcher, final int group, final @Nullable Integer defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Integer.parseInt(str);
    }
  }
}

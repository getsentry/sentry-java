package io.sentry.android.core.internal.tombstone;

import androidx.annotation.Nullable;

/**
 * Used to convert a build/code-id hex string into a little-endian GUID string (from OLE) which is
 * the expected format for the debug-id.
 *
 * <p>Each component used in a GUID uses little-endian representation. But the last two components
 * (`clock_seq` and `node`) were represented in memory as eight individual bytes (which makes them
 * look like big endian when formatted as a string).
 *
 * <p>Conversion example from the sentry development docs:
 *
 * <pre>
 * f1c3bcc0 2798 65fe 3058 404b2831d9e6 4135386c
 *  32-bit   16   16   2x8     6x8       Ignored
 *   LE      LE   LE   LE      LE
 *   =       =    =    =       =
 * c0bcc3f1-9827-fe65-3058-404b2831d9e6
 * </pre>
 *
 * Note: Java bytes are signed. When promoted (e.g. during formatting or bit shifts), they
 * sign-extend to int, unlike uint8_t in C. We therefore mask with & 0xff to preserve the intended
 * unsigned byte values.
 */
public class OleGuidFormatter {
  public static String convert(final @Nullable String hex) {
    if (hex == null) {
      throw new NullPointerException("GUID conversion input hex string");
    }
    if ((hex.length() % 2) != 0) {
      throw new IllegalArgumentException("The GUID conversion input hex string has odd length");
    }
    if (hex.length() < 32) {
      throw new IllegalArgumentException(
          "Need at least 16 bytes (32 hex chars) to convert to GUID");
    }

    final byte[] b = hexToBytes(hex);

    final long timeLow = u32le(b, 0);
    final int timeMid = u16le(b, 4);
    final int timeHiAndVersion = u16le(b, 6);

    return String.format(
        "%08x-%04x-%04x-%02x%02x-%02x%02x%02x%02x%02x%02x",
        timeLow,
        timeMid,
        timeHiAndVersion,
        // clock_seq_hi_and_reserved
        b[8] & 0xff,
        // clock_seq_low
        b[9] & 0xff,
        // node (6 MAC components)
        b[10] & 0xff,
        b[11] & 0xff,
        b[12] & 0xff,
        b[13] & 0xff,
        b[14] & 0xff,
        b[15] & 0xff);
  }

  private static int u16le(byte[] b, int offset) {
    return (b[offset] & 0xff) | ((b[offset + 1] & 0xff) << 8);
  }

  private static long u32le(byte[] b, int offset) {
    return (long) (b[offset] & 0xff)
        | ((long) (b[offset + 1] & 0xff) << 8)
        | ((long) (b[offset + 2] & 0xff) << 16)
        | ((long) (b[offset + 3] & 0xff) << 24);
  }

  private static byte[] hexToBytes(String hex) {
    int numBytes = 16;
    byte[] result = new byte[numBytes];
    for (int byteIdx = 0; byteIdx < numBytes; byteIdx++) {
      int hi = Character.digit(hex.charAt(byteIdx * 2), numBytes);
      int lo = Character.digit(hex.charAt(byteIdx * 2 + 1), numBytes);
      result[byteIdx] = (byte) ((hi << 4) | lo);
    }
    return result;
  }
}

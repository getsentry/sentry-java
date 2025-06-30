package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy apple debug images (MachO).
 *
 * <p>This was also used for non-apple platforms with similar debug setups.
 *
 * <p>A generic (new-style) native platform debug information file.
 *
 * <p>The `type` key must be one of:
 *
 * <p>- `macho` - `elf`: ELF images are used on Linux platforms. Their structure is identical to
 * other native images. - `pe`
 *
 * <p>Examples:
 *
 * <p>```json { "type": "elf", "code_id": "68220ae2c65d65c1b6aaa12fa6765a6ec2f5f434", "code_file":
 * "/lib/x86_64-linux-gnu/libgcc_s.so.1", "debug_id": "e20a2268-5dc6-c165-b6aa-a12fa6765a6e",
 * "image_addr": "0x7f5140527000", "image_size": 90112, "image_vmaddr": "0x40000", "arch": "x86_64"
 * } ```
 *
 * <p>```json { "type": "pe", "code_id": "57898e12145000", "code_file":
 * "C:\\Windows\\System32\\dbghelp.dll", "debug_id": "9c2a902b-6fdf-40ad-8308-588a41d572a0-1",
 * "debug_file": "dbghelp.pdb", "image_addr": "0x70850000", "image_size": "1331200", "image_vmaddr":
 * "0x40000", "arch": "x86" } ```
 *
 * <p>```json { "type": "macho", "debug_id": "84a04d24-0e60-3810-a8c0-90a65e2df61a", "debug_file":
 * "libDiagnosticMessagesClient.dylib", "code_file": "/usr/lib/libDiagnosticMessagesClient.dylib",
 * "image_addr": "0x7fffe668e000", "image_size": 8192, "image_vmaddr": "0x40000", "arch": "x86_64",
 * } ```
 *
 * <p>Proguard mapping file.
 *
 * <p>Proguard images refer to `mapping.txt` files generated when Proguard obfuscates function
 * names. The Java SDK integrations assign this file a unique identifier, which has to be included
 * in the list of images.
 */
public final class DebugImage implements JsonUnknown, JsonSerializable {
  public static final String PROGUARD = "proguard";
  public static final String JVM = "jvm";

  /**
   * The unique UUID of the image.
   *
   * <p>UUID computed from the file contents, assigned by the Java SDK.
   */
  private @Nullable String uuid;

  private @Nullable String type;

  /**
   * Unique debug identifier of the image.
   *
   * <p>- `elf`: Debug identifier of the dynamic library or executable. If a code identifier is
   * available, the debug identifier is the little-endian UUID representation of the first 16-bytes
   * of that identifier. Spaces are inserted for readability, note the byte order of the first
   * fields:
   *
   * <p>```text code id: f1c3bcc0 2798 65fe 3058 404b2831d9e6 4135386c debug id:
   * c0bcc3f1-9827-fe65-3058-404b2831d9e6 ```
   *
   * <p>If no code id is available, the debug id should be computed by XORing the first 4096 bytes
   * of the `.text` section in 16-byte chunks, and representing it as a little-endian UUID (again
   * swapping the byte order).
   *
   * <p>- `pe`: `signature` and `age` of the PDB file. Both values can be read from the CodeView
   * PDB70 debug information header in the PE. The value should be represented as little-endian
   * UUID, with the age appended at the end. Note that the byte order of the UUID fields must be
   * swapped (spaces inserted for readability):
   *
   * <p>```text signature: f1c3bcc0 2798 65fe 3058 404b2831d9e6 age: 1 debug_id:
   * c0bcc3f1-9827-fe65-3058-404b2831d9e6-1 ```
   *
   * <p>- `macho`: Identifier of the dynamic library or executable. It is the value of the `LC_UUID`
   * load command in the Mach header, formatted as UUID.
   */
  private @Nullable String debugId;

  /**
   * Path and name of the debug companion file.
   *
   * <p>- `elf`: Name or absolute path to the file containing stripped debug information for this
   * image. This value might be _required_ to retrieve debug files from certain symbol servers.
   *
   * <p>- `pe`: Name of the PDB file containing debug information for this image. This value is
   * often required to retrieve debug files from specific symbol servers.
   *
   * <p>- `macho`: Name or absolute path to the dSYM file containing debug information for this
   * image. This value might be required to retrieve debug files from certain symbol servers.
   */
  private @Nullable String debugFile;

  /**
   * Optional identifier of the code file.
   *
   * <p>- `elf`: If the program was compiled with a relatively recent compiler, this should be the
   * hex representation of the `NT_GNU_BUILD_ID` program header (type `PT_NOTE`), or the value of
   * the `.note.gnu.build-id` note section (type `SHT_NOTE`). Otherwise, leave this value empty.
   *
   * <p>Certain symbol servers use the code identifier to locate debug information for ELF images,
   * in which case this field should be included if possible.
   *
   * <p>- `pe`: Identifier of the executable or DLL. It contains the values of the `time_date_stamp`
   * from the COFF header and `size_of_image` from the optional header formatted together into a hex
   * string using `%08x%X` (note that the second value is not padded):
   *
   * <p>```text time_date_stamp: 0x5ab38077 size_of_image: 0x9000 code_id: 5ab380779000 ```
   *
   * <p>The code identifier should be provided to allow server-side stack walking of binary crash
   * reports, such as Minidumps.
   *
   * <p>
   *
   * <p>- `macho`: Identifier of the dynamic library or executable. It is the value of the `LC_UUID`
   * load command in the Mach header, formatted as UUID. Can be empty for Mach images, as it is
   * equivalent to the debug identifier.
   */
  private @Nullable String codeId;

  /**
   * Path and name of the image file (required).
   *
   * <p>The absolute path to the dynamic library or executable. This helps to locate the file if it
   * is missing on Sentry.
   *
   * <p>- `pe`: The code file should be provided to allow server-side stack walking of binary crash
   * reports, such as Minidumps.
   */
  private @Nullable String codeFile;

  /**
   * Starting memory address of the image (required).
   *
   * <p>Memory address, at which the image is mounted in the virtual address space of the process.
   * Should be a string in hex representation prefixed with `"0x"`.
   */
  private @Nullable String imageAddr;

  /**
   * Size of the image in bytes (required).
   *
   * <p>The size of the image in virtual memory. If missing, Sentry will assume that the image spans
   * up to the next image, which might lead to invalid stack traces.
   */
  private @Nullable Long imageSize;

  /**
   * CPU architecture target.
   *
   * <p>Architecture of the module. If missing, this will be backfilled by Sentry.
   */
  private @Nullable String arch;

  @SuppressWarnings("unused")
  private @Nullable Map<String, Object> unknown;

  public @Nullable String getUuid() {
    return uuid;
  }

  public void setUuid(final @Nullable String uuid) {
    this.uuid = uuid;
  }

  public @Nullable String getType() {
    return type;
  }

  public void setType(final @Nullable String type) {
    this.type = type;
  }

  public @Nullable String getDebugId() {
    return debugId;
  }

  public void setDebugId(final @Nullable String debugId) {
    this.debugId = debugId;
  }

  public @Nullable String getDebugFile() {
    return debugFile;
  }

  public void setDebugFile(final @Nullable String debugFile) {
    this.debugFile = debugFile;
  }

  public @Nullable String getCodeFile() {
    return codeFile;
  }

  public void setCodeFile(final @Nullable String codeFile) {
    this.codeFile = codeFile;
  }

  public @Nullable String getImageAddr() {
    return imageAddr;
  }

  public void setImageAddr(final @Nullable String imageAddr) {
    this.imageAddr = imageAddr;
  }

  public @Nullable Long getImageSize() {
    return imageSize;
  }

  public void setImageSize(final @Nullable Long imageSize) {
    this.imageSize = imageSize;
  }

  /**
   * Sets the image size.
   *
   * @param imageSize the image size.
   */
  public void setImageSize(long imageSize) {
    this.imageSize = imageSize;
  }

  public @Nullable String getArch() {
    return arch;
  }

  public void setArch(final @Nullable String arch) {
    this.arch = arch;
  }

  public @Nullable String getCodeId() {
    return codeId;
  }

  public void setCodeId(final @Nullable String codeId) {
    this.codeId = codeId;
  }

  // JsonKeys

  public static final class JsonKeys {
    public static final String UUID = "uuid";
    public static final String TYPE = "type";
    public static final String DEBUG_ID = "debug_id";
    public static final String DEBUG_FILE = "debug_file";
    public static final String CODE_ID = "code_id";
    public static final String CODE_FILE = "code_file";
    public static final String IMAGE_ADDR = "image_addr";
    public static final String IMAGE_SIZE = "image_size";
    public static final String ARCH = "arch";
  }

  // JsonUnknown

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (uuid != null) {
      writer.name(JsonKeys.UUID).value(uuid);
    }
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    if (debugId != null) {
      writer.name(JsonKeys.DEBUG_ID).value(debugId);
    }
    if (debugFile != null) {
      writer.name(JsonKeys.DEBUG_FILE).value(debugFile);
    }
    if (codeId != null) {
      writer.name(JsonKeys.CODE_ID).value(codeId);
    }
    if (codeFile != null) {
      writer.name(JsonKeys.CODE_FILE).value(codeFile);
    }
    if (imageAddr != null) {
      writer.name(JsonKeys.IMAGE_ADDR).value(imageAddr);
    }
    if (imageSize != null) {
      writer.name(JsonKeys.IMAGE_SIZE).value(imageSize);
    }
    if (arch != null) {
      writer.name(JsonKeys.ARCH).value(arch);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  // JsonDeserializer

  public static final class Deserializer implements JsonDeserializer<DebugImage> {
    @Override
    public @NotNull DebugImage deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      DebugImage debugImage = new DebugImage();
      Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.UUID:
            debugImage.uuid = reader.nextStringOrNull();
            break;
          case JsonKeys.TYPE:
            debugImage.type = reader.nextStringOrNull();
            break;
          case JsonKeys.DEBUG_ID:
            debugImage.debugId = reader.nextStringOrNull();
            break;
          case JsonKeys.DEBUG_FILE:
            debugImage.debugFile = reader.nextStringOrNull();
            break;
          case JsonKeys.CODE_ID:
            debugImage.codeId = reader.nextStringOrNull();
            break;
          case JsonKeys.CODE_FILE:
            debugImage.codeFile = reader.nextStringOrNull();
            break;
          case JsonKeys.IMAGE_ADDR:
            debugImage.imageAddr = reader.nextStringOrNull();
            break;
          case JsonKeys.IMAGE_SIZE:
            debugImage.imageSize = reader.nextLongOrNull();
            break;
          case JsonKeys.ARCH:
            debugImage.arch = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      debugImage.setUnknown(unknown);
      return debugImage;
    }
  }
}

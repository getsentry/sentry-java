@file:Suppress("MagicNumber", "NestedBlockDepth", "TooManyFunctions")

package io.sentry.android.distribution.internal

import android.content.Context
import android.util.Base64
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

private const val EOCD_MINIMUM_SIZE = 22
private const val EOCD_MAGIC_LE = 0x06054b50
private val EOCD_MAGIC_BE = Integer.reverseBytes(EOCD_MAGIC_LE)

private const val SIGNING_BLOCK_MAGIC = "APK Sig Block 42"
private const val SIGNING_BLOCK_SUFFIX_SIZE = 16 + 8

private const val V2_BLOCK_ID = 0x7109871aL
private const val V3_BLOCK_ID = 0xf05368c0L

private fun readLittleEndianU32(fd: RandomAccessFile): Long {
  var u32 = 0L
  u32 = u32 or (fd.readByte().toLong() and 0xFFL).shl(8 * 0)
  u32 = u32 or (fd.readByte().toLong() and 0xFFL).shl(8 * 1)
  u32 = u32 or (fd.readByte().toLong() and 0xFFL).shl(8 * 2)
  u32 = u32 or (fd.readByte().toLong() and 0xFFL).shl(8 * 3)
  return u32
}

private fun readLittleEndianU64(fd: RandomAccessFile): Long {
  var u64 = 0L
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 0)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 1)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 2)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 3)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 4)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 5)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 6)
  u64 = u64 or (fd.readByte().toLong() and 0xFFL).shl(8 * 7)
  return u64
}

private fun getBinaryIdentifierFromApk(fd: RandomAccessFile): String? {
  val end = fd.length()

  // We make some assumptions below to minimize complexity since setting
  // binaryIdentifier is not critical. The first such assumption is assuming
  // no zip comment:
  val startOfEocd = end - EOCD_MINIMUM_SIZE

  // Find the start of the central directory:
  fd.seek(startOfEocd)
  val eocdMagic = fd.readInt()
  if (eocdMagic != EOCD_MAGIC_BE) {
    return null
  }
  fd.skipBytes(12)
  val cdOffset = readLittleEndianU32(fd)

  // Find (and seek to) the start of the signing block:
  fd.seek(cdOffset - SIGNING_BLOCK_SUFFIX_SIZE)
  val signingBlockSize = readLittleEndianU64(fd)
  val signingBlockMagic = ByteArray(16)
  fd.read(signingBlockMagic)
  if (String(signingBlockMagic, StandardCharsets.UTF_8) != SIGNING_BLOCK_MAGIC) {
    return null
  }
  fd.seek(cdOffset - signingBlockSize)

  val endOfPairs = fd.filePointer + signingBlockSize - SIGNING_BLOCK_SUFFIX_SIZE

  while (fd.filePointer < endOfPairs) {
    val id = readLittleEndianU32(fd)
    val size = readLittleEndianU32(fd)
    val endOfBlock = fd.filePointer + size
    // The structure of V2, V3, V3.1 is the same as far as getting the digests
    // which is all we need to do:
    if (id == V2_BLOCK_ID || id == V3_BLOCK_ID) {
      while (fd.filePointer < endOfBlock) {
        val signerSize = readLittleEndianU32(fd)
        val signerEnd = signerSize + fd.filePointer

        val signedDataSize = readLittleEndianU32(fd)
        val signedDataEnd = signedDataSize + fd.filePointer

        val digestsSize = readLittleEndianU32(fd)
        val digestsEnd = digestsSize + fd.filePointer

        while (fd.filePointer < digestsEnd) {
          val digestSize = readLittleEndianU32(fd)
          val digestEnd = digestSize + fd.filePointer

          // signatureAlgorithmId
          readLittleEndianU32(fd)
          val digestLength = readLittleEndianU32(fd)
          if (digestLength == 32L || digestLength == 64L) {
            val digest = ByteArray(digestLength.toInt())
            fd.read(digest)
            return Base64.encodeToString(digest, Base64.NO_WRAP)
          }

          fd.seek(digestEnd)
        }

        fd.seek(digestsEnd)
        fd.seek(signedDataEnd)
        fd.seek(signerEnd)
      }
    }
    fd.seek(endOfBlock)
  }

  return null
}

/**
 * Get a stable identifier which uniquely identifies a given build of an APK.
 *
 * We attempt to extract the first available digest computed for APK signing from the APK's signing
 * block. This provides a unique identifier for each build that doesn't rely on versionName or
 * versionCode which are often not updated between builds.
 *
 * The implementation reads the APK file directly to extract signing digests following the Android
 * APK signing format. See https://source.android.com/docs/security/features/apksigning
 *
 * @param context Android context to get the application info
 * @return Base64-encoded digest string if found, null otherwise
 */
internal fun getBinaryIdentifier(context: Context): String? {
  return try {
    val info = context.packageManager.getApplicationInfo(context.packageName, 0)
    val path = info.sourceDir
    RandomAccessFile(path, "r").use { fd -> getBinaryIdentifierFromApk(fd) }
  } catch (e: Exception) {
    // If we can't read the APK for any reason, return null
    // This is not critical for the distribution functionality
    null
  }
}

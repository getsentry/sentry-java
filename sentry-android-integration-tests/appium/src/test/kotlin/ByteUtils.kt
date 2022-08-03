import java.lang.Long.signum
import kotlin.math.abs

class ByteUtils {
    companion object {
        const val MiB = 1024 * 1024
        private val SizeChars = arrayOf('K', 'M', 'G', 'T', 'P', 'E')

        fun human(bytes: Long): String {
            val absValue = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
            if (absValue < 1024) {
                return "$absValue B"
            }
            var i = 40
            var c = 0
            var value = absValue
            while (i >= 0 && absValue > 0xfffccccccccccccL shr i) {
                value = value shr 10
                c++
                i -= 10
            }
            value *= signum(bytes).toLong()
            return String.format("%.2f %ciB", value / 1024.0, SizeChars[c])
        }

        fun fromMega(mb: Double): Long {
            return (mb * MiB).toLong()
        }
    }
}

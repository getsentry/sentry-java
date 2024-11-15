package io.sentry.unity

import io.sentry.SentryOptions
import io.sentry.protocol.SentryException
import io.sentry.toInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UnitErrorParserTest {

    @Test
    fun `thread dump garbage`() {
        val lines = Lines.readLines("""
signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
Cause: null pointer dereference
    x0  0000000000000000  x1  0000000000002825  x2  0000000000000006  x3  0000007ffea166d0
    x4  0000000080808080  x5  0000000080808080  x6  0000000080808080  x7  8080808080808080
    x8  00000000000000f0  x9  dae942eb94aaf295  x10 0000000000000000  x11 ffffff80fffffb9f
    x12 0000000000000001  x13 0000004932120029  x14 000b426418bd8caa  x15 0000000000000028
    x16 0000006fa4dda050  x17 0000006fa4db7b00  x18 0000006b670af7b0  x19 0000000000002825
    x20 0000000000002825  x21 00000000ffffffff  x22 0000006cd369e26d  x23 0000006cd369e035
    x24 0000000000000007  x25 0000000071e680a8  x26 0000007ffea169fc  x27 0000007ffea169f0
    x28 0000007ffea16a10  x29 0000007ffea16750
    sp  0000007ffea166b0  lr  0000006fa4d6a7a4  pc  0000006fa4d6a7d0

backtrace:
      #00 pc 000000000004f7d0  /apex/com.android.runtime/lib64/bionic/libc.so (abort+164) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)
      #01 pc 0000000000051574  /apex/com.android.runtime/lib64/bionic/libc.so (fdopendir) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)
      #02 pc 00000000000b2394  /apex/com.android.runtime/lib64/bionic/libc.so (NonPI::MutexLockWithTimeout(pthread_mutex_internal_t*, bool, timespec const*)) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)
      #03 pc 00000000000b2224  /apex/com.android.runtime/lib64/bionic/libc.so (pthread_mutex_lock+192) (BuildId: 5d21548447ff2f9aab8359665aaabf4f)
      #04 pc 0000000000095664  /system/lib64/libc++.so (std::__1::mutex::lock()+8) (BuildId: 1f54b1cc0b8cf4ebefd07b7c5acde867)
      #05 pc 000000000000df14  /system/lib64/libsoundpool.so (android::soundpool::Stream::pause(int)+80) (BuildId: ccd1a6dd5b2e85420412399940942612)
      #06 pc 00000000003603c0  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat
        """.trimIndent().toInputStream().reader().buffered())
        val parser = UnityErrorParser()
        val exception = parser.parse(SentryException(), lines)
        println()
    }
}

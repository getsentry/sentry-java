package io.sentry.android.ndk

import com.nhaarman.mockitokotlin2.mock
import io.sentry.IScopeObserver
import io.sentry.SentryOptions

class NdkScopeObserverTest {

    private class Fixture {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }

        fun getSut(): NdkScopeObserver {
            return NdkScopeObserver(options)
        }
    }

    private val fixture = Fixture()
}

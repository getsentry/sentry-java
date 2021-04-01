package io.sentry.samples.android

import android.support.test.uiautomator.UiDevice
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.microsoft.appcenter.espresso.Factory
import com.microsoft.appcenter.espresso.ReportHelper
import org.junit.After
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    var activityRule: ActivityScenarioRule<MainActivity>
        = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val reportHelper: ReportHelper = Factory.getReportHelper()

    @After
    fun tearDown() {
        reportHelper.label("tearDown")
    }

    // Tests are run in alphabetical order...

    // This one is supposed to fail, as we crash the app to test if an envelope was created
    @Test
    fun a_initSdkAndCrash() {
        reportHelper.label("initSdkAndCrash")

        onView(withId(R.id.init_sdk)).perform(click())
        Thread.sleep(5000)
        onView(withId(R.id.native_crash)).perform(click())
        Thread.sleep(5000)

        // Close either app or android dialog
        val mDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mDevice.pressBack()
    }

    // Check if an envelope is there and we can move it to the cache
    @Test
    fun b_checkIfFileExists() {
        reportHelper.label("checkIfFileExists")

        Thread.sleep(5000)

        onView(withId(R.id.button_copy_file)).perform(click())
        onView(withId(R.id.text_view_copy_file))
            .check(matches(withText("SUCCESS")))
    }
}

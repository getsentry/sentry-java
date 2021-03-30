package io.sentry.samples.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @get:Rule
    var activityRule: ActivityScenarioRule<MainActivity>
        = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun initSdkAndCrash() {
        onView(withId(R.id.init_sdk)).perform(click())
        onView(withId(R.id.native_crash)).perform(click())
    }

    @Test
    fun checkIfFileExists() {
        onView(withId(R.id.button_copy_file)).perform(click())
        onView(withId(R.id.text_view_copy_file))
            .check(matches(withText("SUCCESS")))
    }
}

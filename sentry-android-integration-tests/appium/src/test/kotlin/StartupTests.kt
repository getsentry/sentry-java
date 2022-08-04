import com.google.common.math.Quantiles
import com.google.common.math.Stats
import io.appium.java_client.android.Activity
import io.appium.java_client.android.AndroidDriver
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.math.abs

@Suppress("UnstableApiUsage")
sealed class StartupTests(options: TestOptions) : TestBase(options) {
    private val appsUnderTest: List<AppInfo> = options.appsUnderTest!!
    private val logAppPrefix = "App %-${appsUnderTest.maxOfOrNull { it.name.length }}s"
    abstract val runs: Int

    companion object {
        const val sleepTimeMs: Long = 300

        // See https://en.wikipedia.org/wiki/Interquartile_range#Outliers for details
        private fun filterOutliers(list: List<Long>): List<Long> {
            // sort array (as numbers)
            val sorted = list.sorted()

            val q1 = Quantiles.percentiles().index(25).compute(sorted)
            val q3 = Quantiles.percentiles().index(75).compute(sorted)
            val iqr = q3 - q1

            return list.filter { it.toDouble() in (q1 - 1.5 * iqr)..(q3 + 1.5 * iqr) }
        }
    }

    @Test
    fun `binary size`() {
        // Note: this test doesn't actually need to be part of this class because it doesn't need to install the app.
        //       It's just convenient to stick it here.
        for (app in appsUnderTest) {
            printf("$logAppPrefix size is %s", app.name, ByteUtils.human(app.path.fileSize()))
        }

        if (appsUnderTest.size == 2) {
            val sizes = appsUnderTest.map { it.path.fileSize() }
            val diff = sizes[1] - sizes[0]
            printf(
                "$logAppPrefix is %s %s than app %s",
                appsUnderTest[1].name,
                ByteUtils.human(diff),
                if (diff >= 0) "larger" else "smaller",
                appsUnderTest[0].name
            )

            // fail if the added size is not within the expected range
            diff.shouldBeGreaterThan(ByteUtils.fromMega(1.8))
            diff.shouldBeLessThan(ByteUtils.fromMega(2.0))

            if (options.isCI) {
                println("::set-output name=SizeApp1::${sizes[0]}")
                println("::set-output name=SizeApp2::${sizes[1]}")
                println("::set-output name=SizeDiff::$diff")
            }
        }
    }

    @Test
    fun `startup times`() {
        val measuredTimes = collectStartupTimes()
        val filteredTimes = mutableListOf<List<Long>>()

        for (j in appsUnderTest.indices) {
            val app = appsUnderTest[j]

            val times1 = measuredTimes[j]
            val stats1 = Stats.of(times1)
            printf(
                "$logAppPrefix launch times (original) | mean: %3.2f ms | stddev: %3.2f | %d values: $times1",
                app.name, stats1.mean(), stats1.populationStandardDeviation(), times1.size
            )

            val times2 = filterOutliers(measuredTimes[j])
            val stats2 = Stats.of(times2)
            printf(
                "$logAppPrefix launch times (filtered) | mean: %3.2f ms | stddev: %3.2f | %d values: $times2",
                app.name, stats2.mean(), stats2.populationStandardDeviation(), times2.size
            )

            stats2.populationStandardDeviation().shouldBeLessThan(50.0)
            filteredTimes.add(times2)
        }

        if (appsUnderTest.size == 2) {
            val means = filteredTimes.map { Stats.meanOf(it) }
            val diff = means[1] - means[0]
            printf(
                "$logAppPrefix takes approximately %3f ms %s time to start than app %s",
                appsUnderTest[1].name, abs(diff), if (diff >= 0) "more" else "less", appsUnderTest[0].name
            )

            // fail if the slowdown is not within the expected range
            diff.shouldBeGreaterThan(0.0)
            diff.shouldBeLessThan(150.0)

            if (options.isCI) {
                println("::set-output name=StartTimeApp1::${means[0]}")
                println("::set-output name=StartTimeApp2::${means[1]}")
                println("::set-output name=StartTimeDiff::$diff")
            }
        }
    }

    private fun collectStartupTimes(): List<List<Long>> {
        val measuredTimes = mutableListOf<List<Long>>()

        for (j in appsUnderTest.indices) {
            val app = appsUnderTest[j]

            // sleep before the first test to improve the first run time
            Thread.sleep(1_000)

            // clear logcat before test runs
            if (options.platform == TestOptions.Platform.Android) {
                driver.manage().logs().get("logcat")
            }

            for (i in 1..runs) {
                printf("$logAppPrefix collecting startup times: %d/%d", app.name, i, runs)

                // kill the app and sleep before running the next iteration
                when (options.platform) {
                    TestOptions.Platform.Android -> {
                        val androidDriver = (driver as AndroidDriver)
                        // Note: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
                        androidDriver.startActivity(Activity(app.name, app.activity))
                        androidDriver.terminateApp(app.name)
                    }

                    TestOptions.Platform.IOS -> TODO()
                }

                // sleep before the next test run
                Thread.sleep(sleepTimeMs)
            }

            val appTimes = when (options.platform) {
                TestOptions.Platform.Android -> {
                    // Originally we used a code that loaded a list of executed Appium commands and used the time
                    // that the 'startActivity' command took. It seems like this time includes some overhead of the
                    // Appium controller because the times were about 900 ms, while the time reported in logcat
                    // was `ActivityManager: Displayed io.sentry.java.tests.perf.appplain/.MainActivity: +276ms`
                    //   val times = driver.events.commands.filter { it.name == "startActivity" } .map { it.endTimestamp - it.startTimestamp }
                    //   val offset = j * runs
                    //   times.subList(offset, offset + runs)
                    val logEntries = driver.manage().logs().get("logcat")
                    val regex = Regex("Displayed ${app.name}/\\.${app.activity}: \\+([0-9]+)ms")
                    logEntries.mapNotNull { regex.find(it.message)?.groupValues?.get(1)?.toLong() }
                }

                TestOptions.Platform.IOS -> TODO()
            }

            appTimes.size.shouldBe(runs)
            measuredTimes.add(appTimes)
        }
        return measuredTimes
    }
}

sealed class StartupTestsAndroid(server: TestOptions.Server) :
    StartupTests(
        TestOptions(
            TestOptions.Platform.Android, server,
            listOf(
                AppInfo(
                    "io.sentry.java.tests.perf.appplain",
                    "MainActivity",
                    Path.of("../test-app-plain/build/outputs/apk/release/test-app-plain-release.apk")
                ),
                AppInfo(
                    "io.sentry.java.tests.perf.appsentry",
                    "MainActivity",
                    Path.of("../test-app-sentry/build/outputs/apk/release/test-app-sentry-release.apk")
                )
            )
        )
    )

class StartupTestsAndroidLocal : StartupTestsAndroid(TestOptions.Server.LocalHost) {
    override val runs: Int = 5
}

@SauceLabsOnly
class StartupTestsAndroidSauce : StartupTestsAndroid(TestOptions.Server.SauceLabs) {
    override val runs: Int = 50
}

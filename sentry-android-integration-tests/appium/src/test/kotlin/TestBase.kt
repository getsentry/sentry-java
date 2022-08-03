import io.appium.java_client.AppiumDriver
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import java.util.logging.Level

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIf("sauceLabsSettingsPresentOrCi")
annotation class SauceLabsOnly

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TestBase(
    protected val options: TestOptions
) {
    protected lateinit var driver: AppiumDriver

    companion object {
        @Suppress("unused") // See annotation @SauceLabsOnly
        @JvmStatic
        fun sauceLabsSettingsPresentOrCi(): Boolean {
            return !System.getenv("CI").isNullOrEmpty() || SauceLabs.user.isNotEmpty() || SauceLabs.key.isNotEmpty()
        }
    }

    @Suppress("NOTHING_TO_INLINE") // Inline ensures the logger prints the actual caller.
    protected inline fun printf(format: String, vararg args: Any?, logLevel: Level = Level.INFO) {
        options.logger.log(logLevel, String.format(format, *args))
    }

    @BeforeAll
    fun setUp() {
        driver = options.setup()
    }

    @AfterAll
    fun tearDown() {
        driver.quit()
    }
}

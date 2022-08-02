import io.appium.java_client.AppiumDriver
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.annotation.Target

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIfEnvironmentVariable(named = "SAUCE_ACCESS_KEY", matches = ".+")
annotation class SauceLabsOnly

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TestBase(
    protected val options: TestOptions
) {
    protected lateinit var driver: AppiumDriver

    @BeforeAll
    fun setUp() {
        driver = options.setup()
    }

    @AfterAll
    fun tearDown() {
        driver.quit()
    }
}

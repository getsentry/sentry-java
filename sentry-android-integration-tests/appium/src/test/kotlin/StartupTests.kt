import org.junit.jupiter.api.*

sealed class StartupTests(options: TestOptions) : TestBase(options) {
    @Test
    fun succeedingTest() {
    }

    @Test
    fun failingTest() {
        Assertions.fail<Any>("a failing test")
    }

    @Test
    @Disabled("for demonstration purposes")
    fun skippedTest() {
        // not executed
    }

    @Test
    fun abortedTest() {
        Assumptions.assumeTrue("abc".contains("Z"))
        Assertions.fail<Any>("test should have been aborted")
    }
}

class StartupTestsAndroidLocal : StartupTests(TestOptions(TestOptions.Platform.Android, TestOptions.Server.LocalHost)) {}

@SauceLabsOnly
class StartupTestsAndroidSauce : StartupTests(TestOptions(TestOptions.Platform.Android, TestOptions.Server.SauceLabs)) {}

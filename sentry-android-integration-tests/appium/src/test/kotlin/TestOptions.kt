import com.google.common.collect.ImmutableMap
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.ios.IOSDriver
import io.ktor.util.toLowerCasePreservingASCIIRules
import org.openqa.selenium.MutableCapabilities
import java.net.URL
import java.time.LocalDateTime
import java.util.logging.Logger

class TestOptions(
    val platform: Platform,
    private val server: Server,
    val appsUnderTest: List<AppInfo>? = null
) {
    val logger: Logger = Logger.getLogger("AppiumTest")
    val isCI = System.getenv().containsKey("CI")

    enum class Platform {
        Android,
        IOS
    }

    enum class Server {
        LocalHost,
        SauceLabs
    }

    fun setup(): AppiumDriver {
        val caps = capabilities()
        val url = url()

        if (appsUnderTest != null) {
            when (server) {
                Server.LocalHost -> {
                    val otherAppsPaths = appsUnderTest.map {
                        logger.info("Adding app ${it.name} from ${it.name} 'appium:otherApps'")
                        it.path.toString().replace('\\', '/')
                    }

                    // Local Appium requires JSON array (i.e. a string) instead of a list.
                    caps.setCapability("appium:otherApps", "[\"${otherAppsPaths.joinToString("\", \"")}\"]")
                }

                Server.SauceLabs -> {
                    val otherAppsPaths = appsUnderTest.map {
                        val fileId = SauceLabs.uploadApp(it)
                        val result = "storage:$fileId"
                        logger.info("Adding app ${it.name} from ${it.name} 'appium:otherApps' as '$result")
                        result
                    }

                    // SauceLabs requires this to be a list.
                    caps.setCapability("appium:otherApps", otherAppsPaths)
                }
            }
        }

        logger.info("Launching Appium $platform driver with the following options: ${caps.asMap()}")

        return when (platform) {
            Platform.Android -> AndroidDriver(url, caps)
            Platform.IOS -> IOSDriver(url, caps)
        }
    }

    private fun url() = when (server) {
        Server.LocalHost -> URL("http://127.0.0.1:4723/wd/hub")
        Server.SauceLabs -> URL("https://${SauceLabs.user}:${SauceLabs.key}@ondemand.${SauceLabs.region}.saucelabs.com:443/wd/hub")
    }

    private fun capabilities(): MutableCapabilities {
        val caps = MutableCapabilities()
        caps.setCapability("appium:disableWindowAnimation", true)

        when (server) {
            Server.LocalHost -> {}
            Server.SauceLabs -> {
                val env = System.getenv()
                val sauceOptions = MutableCapabilities()
                // Appium v2 required for Android logcat access.
                sauceOptions.setCapability("appiumVersion", "2.0.0")
                sauceOptions.setCapability("name", "Performance tests")
                sauceOptions.setCapability(
                    "build",
                    if (isCI) "CI ${env["GITHUB_REPOSITORY"]} ${env["GITHUB_REF"]} ${env["GITHUB_RUN_ID"]}"
                    else "Local build ${LocalDateTime.now()}"
                )
                sauceOptions.setCapability(
                    "tags",
                    listOf(platform.toString().toLowerCasePreservingASCIIRules(), if (isCI) "ci" else "local")
                )
                caps.setCapability("sauce:options", sauceOptions)

                // See https://github.com/appium/java-client/issues/1242#issuecomment-539075905
                //   UnsupportedCommandException: unknown command: Cannot call non W3C standard command while in W3C mode
                //   Command: [d3d1a56d-a224-4e05-b99e-a424bb77230d, getLog {type=logcat}]
                caps.setCapability("appium:chromeOptions", ImmutableMap.of("w3c", false))
            }
        }

        when (platform) {
            Platform.Android -> {
                caps.setCapability("platformName", "Android")
                caps.setCapability("appium:automationName", "UiAutomator2")

                if (server == Server.SauceLabs) {
                    // Pixel 4 XL - ARM | octa core | 1785 MHz
                    caps.setCapability("appium:deviceName", "Google Pixel 4 XL")
                    // Pixel 4 XL has three devices, one on each Android 10, 11, 12.
                    // Currently, we allow tests to run on any of them.
                    // caps.setCapability("appium:platformVersion", "11")
                }
            }

            Platform.IOS -> TODO()
        }

        return caps
    }
}

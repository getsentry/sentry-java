package tests;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.openqa.selenium.By;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.appium.java_client.MobileBy;
import io.appium.java_client.android.Activity;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.serverevents.CommandEvent;
import io.appium.java_client.serverevents.ServerEvents;

public class AndroidStartupTest {
    private static final int RUNS = 10;

    // TODO element lookup doesn't work yet
    // private By elementToWaitFor =
    // MobileBy.className("androidx.appcompat.widget.Toolbar");
    private static final String APP_PKG = "io.sentry.samples.instrumentation";
    private static final String APP_ACT = ".ui.MainActivity";
    private static final String APP_WAIT = ".ui.MainActivity";

    private AndroidDriver driver;

    @BeforeMethod
    public void setup(Method method) throws MalformedURLException {

        System.out.println("Sauce - BeforeMethod hook");
        String username = System.getenv("SAUCE_USERNAME");
        String accesskey = System.getenv("SAUCE_ACCESS_KEY");
        String sauceUrl = "@ondemand.us-west-1.saucelabs.com:443";

        String SAUCE_REMOTE_URL = "https://" + username + ":" + accesskey + sauceUrl + "/wd/hub";
        String appName = "android-instrumentation-sample-release.apk";
        // String appID = "9068cfba-d0cd-4027-99dc-ca70c5bf5278";
        String methodName = method.getName();
        URL url = new URL(SAUCE_REMOTE_URL);

        DesiredCapabilities capabilities = new DesiredCapabilities();
        // capabilities.setCapability("deviceName", "Google Pixel 2");
        capabilities.setCapability("platformVersion", "11");
        capabilities.setCapability("platformName", "Android");
        // capabilities.setCapability("automationName", "XCuiTest");
        capabilities.setCapability("app", "storage:filename=" + appName); // or "storage:"+appID
        capabilities.setCapability("name", methodName);
        capabilities.setCapability("autoLaunch", false);

        // capabilities.setCapability("privateDevicesOnly", "true");
        // capabilities.setCapability("platformVersion", "14.3"); //added
        // capabilities.setCapability("appiumVersion", ""); //added
        // capabilities.setCapability("app",
        // "https://github.com/saucelabs/sample-app-mobile/releases/download/2.7.1/iOS.RealDevice.SauceLabs.Mobile.Sample.app.2.7.1.ipa");
        // capabilities.setCapability("noReset", true);
        // capabilities.setCapability("cacheId", "1234");
        // capabilities.setCapability("tags", "sauceDemo1");
        // capabilities.setCapability("build", "myBuild1");
        try {
            driver = new AndroidDriver<>(url, capabilities);
        } catch (Exception e) {
            System.out.println("*** Problem to create the driver " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @AfterMethod
    public void teardown(ITestResult result) {
        if (driver != null) {
            driver.executeScript("sauce:job-result=" + (result.isSuccess() ? "passed" : "failed"));
            driver.quit();
        }
    }

    @Test
    public void testAppLaunch() throws Exception {
        Activity act = new Activity(APP_PKG, APP_ACT);
        act.setAppWaitActivity(APP_WAIT);

        for (int i = 0; i < RUNS; i++) {
            if (i > 0) {
                // kill the app and sleep before running the next iteration
                driver.terminateApp(APP_PKG);
                Thread.sleep(1000);
            }

            driver.startActivity(act);

            // TODO doesn't work yet
            // WebDriverWait wait = new WebDriverWait(driver, 10);
            // wait.until(ExpectedConditions.presenceOfElementLocated(elementToWaitFor));

            // pull out the events
            ServerEvents evts = driver.getEvents();
            List<CommandEvent> cmds = evts.getCommands();
            CommandEvent startActCmd = getCommand(cmds, "startActivity", i);
            // CommandEvent findCmd = getCommand(cmds, "findElement", i);

            long launchMs = startActCmd.endTimestamp - startActCmd.startTimestamp;
            // long interactMs = findCmd.endTimestamp - startActCmd.startTimestamp;

            System.out.println("The app took " + launchMs + "ms to launch");
        }

        // TODO: collect numbers, print the average, stddev
    }

    private static CommandEvent getCommand(List<CommandEvent> list, String name, int index) throws Exception {
        Optional<CommandEvent> result = list.stream()
                .filter((cmd) -> cmd.getName().equals(name))
                .skip(index)
                .findFirst();

        if (!result.isPresent()) {
            throw new Exception("Could not find given command: " + name);
        }
        return result.get();
    }
}

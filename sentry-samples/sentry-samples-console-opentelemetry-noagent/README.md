# Sentry Sample Console

Sample application showing how to use Sentry with OpenTelemetry manually without any framework integration and without java agent.

## How to run? 

To see events triggered in this sample application in your Sentry dashboard, go to `src/main/java/io/sentry/samples/console/Main.java` and replace the test DSN with your own DSN. 

Then, execute a command from the module directory:

```
../../gradlew run
```

# Sentry Sample OpenFeign

Sample application showing how to use instrument Feign HTTP client with Sentry.

## How to run? 

To see transactions triggered in this sample application in your Sentry dashboard, go to `src/main/java/io/sentry/samples/openfeign/Main.java` and replace the test DSN with your own DSN. 

Then, execute a command from the module directory:

```
../../gradlew run
```

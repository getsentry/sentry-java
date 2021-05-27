# Sentry Sample Servlet

Sample application showing how to use Sentry with a non-Spring Java servlet application.

## How to build?

From the module directory execute a command:

```
../../gradlew build
```

## How to run?

To see events triggered in this sample application in your Sentry dashboard, go to `src/main/java/io/sentry/samples/servlet/SentryInitializer.java` and replace the test DSN with your own DSN. 

Deploy `build/libs/sentry-samples-servlet-0.0.1-SNAPSHOT.war` to the servlet container or application server of your choice.

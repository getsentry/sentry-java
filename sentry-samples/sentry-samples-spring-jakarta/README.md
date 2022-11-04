# Sentry Sample Spring 6.0+

Sample application showing how to use Sentry with [Spring](http://spring.io/) from version `6.0` onwards.

## How to run? 

To see events triggered in this sample application in your Sentry dashboard, go to `src/main/java/io/sentry/samples/spring/jakarta/SentryConfig.java` and replace the test DSN with your own DSN. 

Then, execute a command from the module directory:

```
../../gradlew appRun
```

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/sentry-samples-spring-jakarta/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```

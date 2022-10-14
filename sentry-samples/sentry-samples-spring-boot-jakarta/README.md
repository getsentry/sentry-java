# Sentry Sample Spring Boot 3.0+

Sample application showing how to use Sentry with [Spring boot](http://spring.io/projects/spring-boot) from version `3.0` onwards.

## How to run? 

To see events triggered in this sample application in your Sentry dashboard, go to `src/main/resources/application.properties` and replace the test DSN with your own DSN.

Then, execute a command from the module directory:

```
../../gradlew bootRun
```

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```

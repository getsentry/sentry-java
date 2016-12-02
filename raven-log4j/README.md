# Raven-log4j
[log4j](https://logging.apache.org/log4j/1.2/) support for Raven.
It provides an [`Appender`](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html)
for log4j to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>com.getsentry.raven</groupId>
    <artifactId>raven-log4j</artifactId>
    <version>7.8.1</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-log4j%7C7.8.1%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [log4j-1.2.17.jar](https://search.maven.org/#artifactdetails%7Clog4j%7Clog4j%7C1.2.17%7Cjar)
 - [slf4j-log4j12-1.7.7.jar](https://search.maven.org/#artifactdetails%7Corg.slf4j%7Cslf4j-log4j12%7C1.7.7%7Cjar)
 is recommended as the implementation of slf4j (instead of slf4j-jdk14).

## Usage
### Configuration
Add the `SentryAppender` to the `log4j.properties` file:

```properties
log4j.rootLogger=WARN, SentryAppender
log4j.appender.SentryAppender=com.getsentry.raven.log4j.SentryAppender
```

Alternatively in the `log4j.xml` file set:

```
  <appender name="sentry" class="com.getsentry.raven.log4j.SentryAppender">
    <filter class="org.apache.log4j.varia.LevelRangeFilter">
      <param name="levelMin" value="WARN"/>
    </filter>
  </appender>
```

You'll also need to associate the `sentry` appender with your root logger, like so:

```
  <root>
    <priority value="info" />
    <appender-ref ref="my-other-appender" />
    <appender-ref ref="sentry" />
  </root>
```

Next, you'll need to configure your DSN (client key) and optionally other
values such as `environment` and `release`. See below for the two
ways you can do this.

#### Configuration via runtime environment

This is the most flexible method to configure the `SentryAppender`,
because it can be easily changed based on the environment you run your
application in.

The following can be set as System Environment variables:

```bash
SENTRY_EXAMPLE=xxx java -jar app.jar
```

or as Java System Properties:

```bash
java -Dsentry.example=xxx -jar app.jar
```

Configuration parameters follow:

| Environment variable | Java System Property | Example value | Description |
|---|---|---|---|
| `SENTRY_DSN` | `sentry.dsn` | `https://host:port/1?options` | Your Sentry DSN (client key), if left blank Raven will no-op |
| `SENTRY_RELEASE` | `sentry.release` | `1.0.0` | Optional, provide release version of your application |
| `SENTRY_ENVIRONMENT` | `sentry.environment` | `production` | Optional, provide environment your application is running in |
| `SENTRY_SERVERNAME` | `sentry.servername` | `server1` | Optional, override the server name (rather than looking it up dynamically) |
| `SENTRY_RAVENFACTORY` | `sentry.ravenfactory` | `com.foo.RavenFactory` | Optional, select the ravenFactory class |
| `SENTRY_TAGS` | `sentry.tags` | `tag1:value1,tag2:value2` | Optional, provide tags |
| `SENTRY_EXTRA_TAGS` | `sentry.extratags` | `foo,bar,baz` | Optional, provide tag names to be extracted from MDC when using SLF4J |

#### Configuration via `log4j.properties`

You can also configure everything statically within the `log4j.properties` file
itself. This is less flexible because it's harder to change when you run
your application in different environments.

```properties
log4j.rootLogger=WARN, SentryAppender
log4j.appender.SentryAppender=com.getsentry.raven.log4j.SentryAppender
log4j.appender.SentryAppender.dsn=https://host:port/1?options
// Optional, provide release version of your application
log4j.appender.SentryAppender.release=1.0.0
// Optional, provide environment your application is running in
log4j.appender.SentryAppender.environment=production
// Optional, override the server name (rather than looking it up dynamically)
log4j.appender.SentryAppender.serverName=server1
# Optional, select the ravenFactory class
#log4j.appender.SentryAppender.ravenFactory=com.foo.RavenFactory
// Optional, provide tags
log4j.appender.SentryAppender.tags=tag1:value1,tag2:value2
# Optional, provide tag names to be extracted from MDC when using SLF4J
log4j.appender.SentryAppender.extraTags=foo,bar,baz
```

### Additional data and information
It's possible to add extra data to events,
thanks to both [the MDC](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html)
and [the NDC](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html) systems provided by Log4j.

### In practice
```java
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;

public class MyClass {
    private static final Logger logger = Logger.getLogger(MyClass.class);

    void logSimpleMessage() {
        // This adds a simple message to the logs
        logger.info("This is a test");
    }

    void logWithExtras() {
        // MDC extras
        MDC.put("extra_key", "extra_value");
        // NDC extras are sent under 'log4J-NDC'
        NDC.push("Extra_details");
        // This adds a message with extras to the logs
        logger.info("This is a test");
    }

    void logException() {
        try {
            unsafeMethod();
        } catch (Exception e) {
            // This adds an exception to the logs
            logger.error("Exception caught", e);
        }
    }

    void unsafeMethod() {
        throw new UnsupportedOperationException("You shouldn't call that");
    }
}
```

### Mapped Tags
By default all MDC parameters are sent under the Additional Data Tab. By specify the extraTags parameter in your
configuration file. You can specify MDC keys to send as tags instead of including them in Additional Data.
This allows them to be filtered within Sentry.

```properties
log4j.appender.SentryAppender.extraTags=User,OS
```
```java
    void logWithExtras() {
        // MDC extras
        MDC.put("User", "test user");
        MDC.put("OS", "Linux");

        // This adds a message with extras and MDC keys declared in extraTags as tags to Sentry
        logger.info("This is a test");
    }
```

## Asynchronous logging
It is not recommended to attempt to set up `SentryAppender` within an
[AsyncAppender](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html).
While this is a common solution to avoid blocking the current thread until the
event is sent to Sentry, it is recommended to rely instead on the asynchronous
connection provided by Raven.

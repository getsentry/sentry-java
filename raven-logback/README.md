# Raven-logback
[logback](http://logback.qos.ch/) support for Raven.
It provides an [`Appender`](http://logback.qos.ch/apidocs/ch/qos/logback/core/Appender.html)
for logback to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>com.getsentry.raven</groupId>
    <artifactId>raven-logback</artifactId>
    <version>7.8.1</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-logback%7C7.8.1%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [logback-core-1.1.2.jar](https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-core%7C1.1.2%7Cjar)
 - [logback-classic-1.1.2.jar](https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-classic%7C1.1.2%7Cjar)
 will act as the implementation of slf4j (instead of slf4j-jdk14).

## Usage
### Configuration
Add the `SentryAppender` to your `logback.xml` file:

```xml
<configuration>
    <appender name="Sentry" class="com.getsentry.raven.logback.SentryAppender" />
    <root level="warn">
        <appender-ref ref="Sentry"/>
    </root>
</configuration>
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

#### Configuration via `logback.xml`

You can also configure everything statically within the `logback.xml` file
itself. This is less flexible because it's harder to change when you run
your application in different environments.

```xml
<configuration>
    <appender name="Sentry" class="com.getsentry.raven.logback.SentryAppender">
        <dsn>https://host:port/1?options</dsn>
        <!-- Optional, provide release version of your application -->
        <release>1.0.0</release>
        <!-- Optional, provide environment your application is running in -->
        <environment>production</environment>
        <!-- Optional, override the server name (rather than looking it up dynamically) -->
        <serverName>server1</serverName>
        <!-- Optional, select the ravenFactory class -->
        <ravenFactory>com.foo.RavenFactory</ravenFactory>
        <!-- Optional, provide tags -->
        <tags>tag1:value1,tag2:value2</tags>
        <!-- Optional, provide tag names to be extracted from MDC when using SLF4J -->
        <extraTags>foo,bar,baz</extraTags>
    </appender>
    <root level="warn">
        <appender-ref ref="Sentry"/>
    </root>
</configuration>
```

### Additional data and information
It's possible to add extra details to events captured by the logback module
thanks to the [marker system](http://www.slf4j.org/faq.html#fatal) which will
add a tag `logback-Marker`.
[The MDC system provided by logback](http://logback.qos.ch/manual/mdc.html)
allows to add extra information to the event.

### Mapped Tags
By default all MDC parameters are sent under the Additional Data Tab. By specifying the extraTags parameter in your
configuration file. You can specify MDC keys to send as tags instead of including them in Additional Data.
This allows them to be filtered within Sentry.

```xml
<extraTags>User,OS</extraTags>
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

### In practice
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

public class MyClass {
    private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
    private static final Marker MARKER = MarkerFactory.getMarker("myMarker");

    void logSimpleMessage() {
        // This adds a simple message to the logs
        logger.info("This is a test");
    }

    void logWithTag() {
        // This adds a message with a tag to the logs named 'logback-Marker'
        logger.info(MARKER, "This is a test");
    }

    void logWithExtras() {
        // MDC extras
        MDC.put("extra_key", "extra_value");
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

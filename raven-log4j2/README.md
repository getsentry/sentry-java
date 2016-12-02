# Raven-Log4j 2
[Log4j 2](https://logging.apache.org/log4j/2.x/) support for Raven.
It provides an [`Appender`](https://logging.apache.org/log4j/2.x/log4j-core/apidocs/org/apache/logging/log4j/core/Appender.html)
for Log4j 2 to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>com.getsentry.raven</groupId>
    <artifactId>raven-log4j2</artifactId>
    <version>7.8.1</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-log4j2%7C7.8.1%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [log4j-api-2.1.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-api%7.8.1%7Cjar)
 - [log4j-core-2.1.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-core%7.8.1%7Cjar)
 - [log4j-slf4j-impl-2.1.jar](http://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-slf4j-impl%7.8.1%7Cjar)
 is recommended as the implementation of slf4j (instead of slf4j-jdk14).


## Usage
### Configuration
Add the `SentryAppender` to your `log4j2.xml` file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration status="warn" packages="org.apache.logging.log4j.core,com.getsentry.raven.log4j2">
    <appenders>
        <Raven name="Sentry" />
    </appenders>

    <loggers>
        <root level="all">
            <appender-ref ref="Sentry"/>
        </root>
    </loggers>
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

#### Configuration via `log4j2.xml`

You can also configure everything statically within the `log4j2.xml` file
itself. This is less flexible because it's harder to change when you run
your application in different environments.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration status="warn" packages="org.apache.logging.log4j.core,com.getsentry.raven.log4j2">
    <appenders>
        <Raven name="Sentry">
            <dsn>
                https://host:port/1?options
            </dsn>
            <!--
                Optional, provide release version of your application
            -->
            <release>
                1.0.0
            </release>
            <!--
                Optional, provide environment your application is running in
            -->
            <environment>
                production
            </environment>
            <!--
                Optional, override the server name (rather than looking it up dynamically)
            -->
            <serverName>
                server1
            </serverName>
            <!--
                Optional, select the ravenFactory class
            -->
            <!--
            <ravenFactory>
                com.foo.RavenFactory
            </ravenFactory>
            -->
            <!--
                Optional, provide tags
            -->
            <tags>
                tag1:value1,tag2:value2
            </tags>
            <!--
                Optional, provide tag names to be extracted from MDC when using SLF4J
            -->
            <extraTags>
                foo,bar,baz
            </extraTags>
        </Raven>
    </appenders>

    <loggers>
        <root level="all">
            <appender-ref ref="Sentry"/>
        </root>
    </loggers>
</configuration>
```

### Additional data and information
It's possible to add extra details to events captured by the Log4j 2 module
thanks to the [marker system](https://logging.apache.org/log4j/2.x/manual/markers.html)
which will add a tag `log4j2-Marker`.
Both [the MDC and the NDC systems provided by Log4j 2](https://logging.apache.org/log4j/2.x/manual/thread-context.html)
are usable, allowing to attach extras information to the event.

### Mapped Tags
By default all Thread Context parameters are sent under the Additional Data Tab. By specifying the extraTags parameter in your
configuration file. You can specify Thread Context keys to send as tags instead of including them in Additional Data.
This allows them to be filtered within Sentry. In older Log4j versions, the Thread Context Map was known as the MDC.

```xml
<extraTags>
    User,OS
</extraTags>
```
```java
    void logWithExtras() {
        // ThreadContext extras
        ThreadContext.put("User", "test user");
        ThreadContext.put("OS", "Linux");

        // This adds a message with extras and ThreadContext keys declared in extraTags as tags to Sentry
        logger.info("This is a test");
    }
```

### In practice
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class MyClass {
    private static final Logger logger = LogManager.getLogger(MyClass.class);
    private static final Marker MARKER = MarkerManager.getMarker("myMarker");

    void logSimpleMessage() {
        // This adds a simple message to the logs
        logger.info("This is a test");
    }

    void logWithTag() {
        // This adds a message with a tag to the logs named 'log4j2-Marker'
        logger.info(MARKER, "This is a test");
    }

    void logWithExtras() {
        // MDC extras
        ThreadContext.put("extra_key", "extra_value");
        // NDC extras are sent under 'log4j2-NDC'
        ThreadContext.push("Extra_details");
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

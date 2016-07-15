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
    <version>7.4.0</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Ccom.getsentry.raven%7Craven-log4j2%7C7.4.0%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [log4j-api-2.1.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-api%7.4.0%7Cjar)
 - [log4j-core-2.1.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-core%7.4.0%7Cjar)
 - [log4j-slf4j-impl-2.1.jar](http://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-slf4j-impl%7.4.0%7Cjar)
 is recommended as the implementation of slf4j (instead of slf4j-jdk14).


## Usage
### Configuration
In the `log4j2.xml` file set:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration status="warn" packages="org.apache.logging.log4j.core,com.getsentry.raven.log4j2">
    <appenders>
        <Raven name="Sentry">
            <dsn>
                https://publicKey:secretKey@host:port/1?options
            </dsn>
            <!--
                Optional, provide tags
            -->
            <tags>
                tag1:value1,tag2:value2
            </tags>
            <!--
                Optional, provide release version of your application
            -->
            <release>
                1.0.0
            </release>
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
                com.getsentry.raven.DefaultRavenFactory
            </ravenFactory>
            -->
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

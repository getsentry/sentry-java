# Raven-log4j2
[log4j2](https://logging.apache.org/log4j/2.x/) support for Raven.
It provides an [`Appender`](https://logging.apache.org/log4j/2.x/log4j-core/apidocs/org/apache/logging/log4j/core/Appender.html)
for log4j to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>net.kencochrane.raven</groupId>
    <artifactId>raven-log4j2</artifactId>
    <version>4.0</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Cnet.kencochrane.raven%7Craven-log4j2%7C4.0%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [log4j-api-2.0-beta7.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-api%7C2.0-beta7%7Cjar)
 - [log4j-core-2.0-beta7.jar](https://search.maven.org/#artifactdetails%7Corg.apache.logging.log4j%7Clog4j-core%7C2.0-beta7%7Cjar)


## Usage
### Configuration
In the `log4j2.xml` file set:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration status="warn" packages="org.apache.logging.log4j.core,net.kencochrane.raven.log4j2">
    <appenders>
        <Raven name="Sentry">
            <dsn>
                https://publicKey:secretKey@host:port/1?options
            </dsn>
        </Raven>
    </appenders>

    <loggers>
        <root level="all">
            <appender-ref ref="Sentry"/>
        </root>
    </loggers>
</configuration>
```

### In practice
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MyClass {
    private static final Logger logger = LogManager.getLogger(MyClass.class);

    void logSimpleMessage() {
        // This adds a simple message to the logs
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

### Extras
**TODO:**
The log4j2 module for raven takes advantage of the [marker system](https://logging.apache.org/log4j/2.x/manual/markers.html).
It is also possible use both the [MDC and the NDC systems provided by log4j2](https://logging.apache.org/log4j/2.x/manual/thread-context.html)

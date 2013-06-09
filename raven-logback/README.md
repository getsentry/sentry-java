# Raven-logback
[logback](http://logback.qos.ch/) support for Raven.
It provides an [`Appender`](http://logback.qos.ch/apidocs/ch/qos/logback/core/Appender.html)
for logback to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>net.kencochrane.raven</groupId>
    <artifactId>raven-logback</artifactId>
    <version>4.0</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Cnet.kencochrane.raven%7Craven-logback%7C4.0%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [logback-core-1.0.13.jar](https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-core%7C1.0.13%7Cjar)
 - [logback-classic-1.0.13.jar](https://search.maven.org/#artifactdetails%7Cch.qos.logback%7Clogback-classic%7C1.0.13%7Cjar)
 will act as the implementation of slf4j (instead of slf4j-jdk14).

## Usage
### Configuration
In the `logback.xml` file set:

```xml
<configuration>
    <appender name="Sentry" class="net.kencochrane.raven.logback.SentryAppender">
        <dsn>https://publicKey:secretKey@host:port/1?options</dsn>
    </appender>
    <root level="debug">
        <appender-ref ref="Sentry"/>
    </root>
</configuration>
```

### In practice
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyClass {
    private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

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
The logback module for raven takes advantage of the [marker system](http://www.slf4j.org/faq.html#fatal).
It is also possible use the [MDC system provided by logback](http://logback.qos.ch/manual/mdc.html).

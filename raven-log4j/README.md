# Raven-log4j
[log4j](https://logging.apache.org/log4j/1.2/) support for Raven.
It provides an [`Appender`](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html)
for log4j to send the logged events to Sentry.

## Installation

### Maven
```xml
<dependency>
    <groupId>net.kencochrane.raven</groupId>
    <artifactId>raven-log4j</artifactId>
    <version>4.1.2</version>
</dependency>
```

### Other dependency managers
Details in the [central Maven repository](https://search.maven.org/#artifactdetails%7Cnet.kencochrane.raven%7Craven-log4j%7C4.1.2%7Cjar).

### Manual dependency management
Relies on:

 - [raven dependencies](../raven)
 - [log4j-1.2.17.jar](https://search.maven.org/#artifactdetails%7Clog4j%7Clog4j%7C1.2.17%7Cjar)
 - [slf4j-log4j12-1.7.5.jar](https://search.maven.org/#artifactdetails%7Corg.slf4j%7Cslf4j-log4j12%7C1.7.5%7Cjar)
 is recommended as the implementation of slf4j (instead of slf4j-jdk14).

## Usage
### Configuration
In the `log4j.properties` file set:

```properties
log4j.rootLogger=WARN, SentryAppender
log4j.appender.SentryAppender=net.kencochrane.raven.log4j.SentryAppender
log4j.appender.SentryAppender.dsn=https://publicKey:secretKey@host:port/1?options
log4j.appender.SentryAppender.tags=tag1:value1,tag2:value2
# Optional, allows to select the ravenFactory
#log4j.appender.SentryAppender.ravenFactory=net.kencochrane.raven.DefaultRavenFactory
```

### Additional data and information
It's possible to add extra details to events captured by the Log4j module
thanks to both [the MDC](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/MDC.html)
and [the NDC](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/NDC.html) systems provided by Log4j are
usable, allowing to attach extras information to the event.

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

## Asynchronous logging
It is not recommended to attempt to set up `SentryAppender` within an
[AsyncAppender](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html).
While this is a common solution to avoid blocking the current thread until the
event is sent to Sentry, it is recommended to rely instead on the asynchronous
connection provided by Raven.

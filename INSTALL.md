# Installation and setup of Raven

## Installing Raven
Currently there are 6 modules in the project:

 - `raven`, the core of the project, providing the client and support for JUL
 - `raven-log4j`, Appender for log4j
 - `raven-log4j2`, Appender for log4j2
 - `raven-logback`, Appender for Logback
 - `sentry-stub`, Sentry server stub, allowing to test the protocol

### Build
It's possible to get the latest version of Raven by building it from the
sources.

    $ git clone https://github.com/kencochrane/raven-java.git
    $ cd raven-java
    $ mvn clean install -DskipTests=true

### Maven dependency
To add raven as a dependency, simply add this to the pom.xml file:

    <dependency>
      <groupId>net.kencochrane.raven</groupId>
      <artifactId>raven</artifactId>
      <version>4.0</version>
    </dependency>

Other modules can be added the same way (replacing the `artifactId`) with the
name of the module.
It is preferable to use the scope `runtime` if the API is used through a
logging system.

If the version is a snapshot it will be necessary to specify the
Sonatype Nexus snapshot repository:

    <repository>
        <id>sonatype-nexus-snapshots</id>
        <name>Sonatype Nexus Snapshots</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>

### Manual installation
TODO

## Using Raven

### Manual usage

    import net.kencochrane.raven.Raven;
    import net.kencochrane.raven.event.EventBuilder;

    public class Example {
        public static void main(String[] args) {
            // The DSN from Sentry: "http://public:private@host:port/1"
            String rawDsn = args[0];
            Raven client = new Raven(rawDsn);
            EventBuilder eventBuilder = new EventBuilder()
                            .setMessage("Hello from Raven!");
            client.sendEvent(eventBuilder.build());
        }
    }

It is also possible to create a client without directly providing a DSN,
the DSN will then be determined at runtime (when the client is created).

The client will lookup for the first DSN configuration provided:

 - JNDI in `java:comp/env/sentry/dsn`
 - The environment variable `SENTRY_DSN`
 (`export SENTRY_DSN=yoursentrydsn` or `setenv SENTRYDSN yoursentrydsn`)
 - The system property `SENTRY_DSN` (`-DSENTRY_DSN=yoursentrydsn`)

    import net.kencochrane.raven.Raven;
    import net.kencochrane.raven.event.EventBuilder;

    public class Example {
        public static void main(String[] args) {
            Raven client = new Raven();
            EventBuilder eventBuilder = new EventBuilder()
                            .setMessage("Hello from Raven!");
            client.sendEvent(eventBuilder.build());
        }
    }

### Using `java.util.logging`
To use the `SentryHandler` with `java.util.loggin` use this `logging.properties`

    level=INFO
    handlers=net.kencochrane.raven.jul.SentryHandler
    net.kencochrane.raven.jul.SentryHandler.dsn=http://publicKey:secretKey@host:port/1?options


### Using log4j
To use the `SentryAppender` with log4j use this configuration:

    log4j.rootLogger=DEBUG, SentryAppender
    log4j.appender.SentryAppender=net.kencochrane.raven.log4j.SentryAppender
    log4j.appender.SentryAppender.dsn=http://publicKey:secretKey@host:port/1?options

#### Asynchronous logging with AsyncAppender
It is not recommended to attempt to set up a `SentryAppender` with an
[AsyncAppender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html).
While this is a common solution to avoid blocking the current thread until the
event is sent to Sentry, it is recommended to rely instead on the asynchronous
connection within Raven.

### Using log4j2
To use the `SentryAppender` with log4j2 use this configuration:

    <?xml version="1.0" encoding="UTF-8"?>
    <configuration status="warn" packages="org.apache.logging.log4j.core,net.kencochrane.raven.log4j2">
        <appenders>
            <Raven name="Sentry">
                <dsn>
                    http://publicKey:secretKey@host:port/1?options
                </dsn>
            </Raven>
        </appenders>

        <loggers>
            <root level="all">
                <appender-ref ref="Sentry"/>
            </root>
        </loggers>
    </configuration>


### Using logback
To use the `SentryAppender` with logback use this configuration:

    <configuration>
        <appender name="Sentry" class="net.kencochrane.raven.logback.SentryAppender">
            <dsn>
                http://publicKey:secretKey@host:port/1?options
            </dsn>
        </appender>

        <root level="debug">
            <appender-ref ref="Sentry"/>
        </root>
    </configuration>

### Capturing the HTTP environment
**TODO**

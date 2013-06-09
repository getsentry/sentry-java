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

It is also possible to create a client without directly providing a DSN,
the DSN will then be determined at runtime (when the client is created).

The client will lookup for the first DSN configuration provided:

 - JNDI in `java:comp/env/sentry/dsn`
 - The environment variable `SENTRY_DSN`
 (`export SENTRY_DSN=yoursentrydsn` or `setenv SENTRYDSN yoursentrydsn`)
 - The system property `SENTRY_DSN` (`-DSENTRY_DSN=yoursentrydsn`)

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

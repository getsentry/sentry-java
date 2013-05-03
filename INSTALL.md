# Installation and setup of Raven

## Installing Raven
Currently there are 6 modules in the project:

 - `raven`, the core of the project, providing the client and support for JUL
 - `raven-legacy`, support of the Raven-2.0 API (it's recommended to move
 to the new API as legacy will be removed in the future releases of Raven)
 - `raven-log4j`, Appender for log4j
 - `raven-log4j2`, Appender for log4j2
 - `raven-logback`, Appander for Logback
 - `sentry-stub`, Sentry server stub, allowing to test the protocol

### Build
It's possible to get the latest version of Raven by building it from the
sources.

    $ git clone https://github.com/kencochrane/raven-java.git
    $ cd raven-java
    $ mvn clean install -DskipTests=true

_Due to a [known issue](https://bugs.eclipse.org/bugs/show_bug.cgi?id=405631) in
jetty, it is currently not possible to run the integration tests from the main
project. They have to be run manually on each module independently._

### Maven dependency
To add raven as a dependency, simply add this to your pom.xml:

    <dependency>
      <groupId>net.kencochrane.raven</groupId>
      <artifactId>raven</artifactId>
      <version>4.0-SNAPSHOT</version>
    </dependency>

You can add the other modules the same way (replacing the `artifactId`) with the
name of the module.

### Manual installation

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
TODO

### Using log4j
To use the `SentryAppender` with log4j use this configuration:

    log4j.rootLogger=DEBUG, SentryAppender
    log4j.appender.SentryAppender=net.kencochrane.raven.log4j.SentryAppender
    log4j.appender.SentryAppender.dsn=http://publicKey:secretKey@host:port/projectId?options

#### Asynchronous logging with AsyncAppender
It is not recommended to attempt to set up a `SentryAppender` with an
[AsyncAppender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html).
While this is a common solution to avoid blocking the current thread until the
event is sent to Sentry, it is recommended to use instead the option
`raven.async` to enable asynchronous logging for raven.

### Using log4j2
**TODO**

### Using logback
**TODO**

### Capturing the HTTP environment
**TODO**

# Raven-Java

Raven-Java is a Java client for [Sentry](https://www.getsentry.com/).
Besides the regular client you can use within your application code, this
project also provides tools allowing the most popular logging frameworks
to send the logs directly to sentry:

 - `java.util.logging` is supported out of the box.
 - `raven-log4j` adds the support for [log4j](https://logging.apache.org/log4j/1.2/).
 - `raven-log4j2` adds the support for [log4j2](https://logging.apache.org/log4j/2.x/).
 - `raven-logback` adds the support for [logback](http://logback.qos.ch/).

Raven-Java supports both HTTP(S) and UDP transport of events.

## Sentry Protocol and supported versions
### Sentry Protocol versions
Since the version 3.0, Raven-Java the versionning system is based on the
protocol version of Sentry. This means that Raven-Java 3.0 only supports
the version 3 of Sentry's protocol while Raven-Java 4.0 only supports
the version 4.

Sentry only supports the last two major releases of the protocol, for this
reason, only the last two major versions of Raven-Java are maintained.

### Sentry versions

 - Sentry protocol v4 is not yet available (use Raven-Java 4.0)
 - Sentry protocol v3 is available since Sentry 5.1 (use Raven-Java 3.0)
 - Sentry protocol v2 is available since Sentry 2.0 (use Raven-Java 2.0)

## Build and Installation
Currently there are 6 modules in the project:

 - `raven`, the core of the project, providing the client and support for JUL
 - `raven-legacy`, support of the Raven-Java 2.0 API (it's recommended to move
 to the new API as legacy will be removed in the future releases of Raven-Java)
 - `raven-log4j`, Appender for log4j
 - `raven-log4j2`, Appender for log4j2
 - `raven-logback`, Appander for Logback
 - `sentry-stub`, Sentry server stub, allowing to test the protocol
 
### Build
It's possible to get the latest version of Raven-Java by building it from the
sources.

    $ git clone https://github.com/kencochrane/raven-java.git
    $ cd raven-java
    $ mvn clean install -DskipTests=true

_Due to a [known issue](https://bugs.eclipse.org/bugs/show_bug.cgi?id=405631) in
jetty, it is currently not possible to run the integration tests from the main
project. They have to be run manually on each module independently._

### Maven dependency
To add raven-java as a dependency, simply add this to your pom.xml:

    <dependency>
      <groupId>net.kencochrane.raven</groupId>
      <artifactId>raven</artifactId>
      <version>4.0-SNAPSHOT</version>
    </dependency>

You can add the other modules the same way (replacing the `artifactId`) with the
name of the module.

### Manual installation

## Using Raven-Java

### Manual usage

    import net.kencochrane.raven.Raven;
    import net.kencochrane.raven.event.EventBuilder;
    
    public class Example {
        public static void main(String[] args) {
            // The DSN from Sentry: "http://public:private@host:port/1"
            String rawDsn = args[0];
            Raven client = new Raven(rawDsn);
            EventBuilder eventBuilder = new EventBuilder()
                            .setMessage("Hello from Raven-Java!");
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
                            .setMessage("Hello from Raven-Java!");
            client.sendEvent(eventBuilder.build());
        }
    }

### Using `java.util.logging`
TODO

### Using log4j
TODO

#### Asynchronous logging with AsyncAppander
log4j supports asynchronous logging with
[AsyncAppender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html).
While this is a common solution to avoid blocking the current thread until the
event is sent to Sentry, it is recommended to use instead the option
`raven.async` to enable asynchronous logging for raven.

Some 

### Using log4j2
**TODO**

### Using logback
**TODO**

### Capturing the HTTP environment
**TODO**

## Connection and protocol
It is possible to send events to Sentry over different protocols, depending
on the security and performance requirements.
So far Sentry accepts HTTP(S) and UDP which are both fully supported by
Raven-Java.

### HTTP
The most common way to access Sentry is through HTTP, this can be done by
using a DSN using this form:

    http://public:private@host:port/1
    
If not provided, the port will default to `80`.

### HTTPS
It is possible to use an encrypted connection to Sentry using HTTPS:

    https://public:private@host:port/1
    
If not provided, the port will default to `443`.
### HTTPS (naive)
If the certificate used over HTTPS is a wildcard certificate (which is not
handled by every version of Java), and the certificate isn't added to the 
truststore, it is possible to add a protocol setting to tell the client to be
naive and ignore the hostname verification:

    naive+https://public:private@host:port/1

### UDP
It is possible to use a DSN with the UDP protocol:

    udp://public:private@host:port/1

If not provided the port will default to `9001`.

While being faster because there is no TCP and HTTP overhead, UDP doesn't wait
for a reply, and if a connection problem occurs, there will be no notification.

## Options
It is possible to enable some options by adding data to the query string of the
DSN:

    http://public:private@host:port/1?option1=value1&option2&option3=value3

Not every option requires a value.

### Async connection
In order to avoid performance issues due to a large amout of logs being
generated or a slow connection to the Sentry server, it is recommended to use
the asynchronous connection which will use a low priority thread pool to submit
events to Sentry.

To enable the async mode, add the `raven.async` option to your DSN:

    http://public:private@host:port/1?raven.async

#### Threads count (advanced)
By default the thread pool used by the async connection contains one thread per
processor available to the JVM (more threads wouldn't be useful).

It's possible to manually set the number of threads (for example if you want
only one Thread) with the option `raven.async.threads`:

    http://public:private@host:port/1?raven.async&raven.async.threads=1

#### Threads priority (advanced)
As in most cases sending logs to Sentry isn't as important as an application
running smoothly, the threads have a
[minimal priority](http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY).

It is possible to customise this value to increase the priority of those threads
with the option `raven.async.priority`:

    http://public:private@host:port/1?raven.async.priority=10&raven.async

### Inapp classes
**TODO**

### Compression
By default the content sent to Sentry is compressed and encoded in base64 before
being sent.
This operation allows to send a smaller amount of data for each event.
However compressing and encoding the data adds a CPU and memory overhead which
might not be useful if the connection to Sentry is fast and reliable.

Depending on the limitations of the project (ie: a mobile application with a
limited connection, Sentry hosted on an external network), it can be interesting
to compress the data beforehand or not.

It's possible to disable the compression with the option `raven.nocompression`

    http://public:private@host:port/1?raven.nocompression

### Timeout (advanced)
To avoid blocking the thread because of a connection taking too much time, a
timeout can be set by the connection.

By default the connection will set up its own timeout, but it's possible to
manually set one with `raven.timeout` (in milliseconds):

    http://public:private@host:port/1?raven.timeout=10000

## History

- 4.0
    - Support of the Sentry protocol V4
- 3.0
    - Support of the Sentry protocol V3
    - Rewritten
    - Added log4j2 appender
    - Support of JNDI
- 2.0
    - Version increment to reduce confusion about releases
    - Added Logback appender (thanks to [ccouturi](https://github.com/ccouturi))
- 1.0
    - Rewrite
    - Support tags
    - Added support for JSON processors (see bundled `ServletJSONProcessor`)
- 0.6
    - Added support for sending messages through UDP
- 0.5
    - Added async support
    - Fixed issue with parsing of path and port in DSN
- 0.4
    - Added the ability to get the SENTRY_DSN from the ENV
    - Added RavenClient.captureMessage
    - Added RavenClient.captureException
- 0.3
    - Added Maven support
    - Merged with log4sentry project by Kevin Wetzels
    - Added Proxy support
    - Added full stack trace to logs

- 0.2
    - code refactor and cleanup

- 0.1
    - initial version

## Contributors

- [Ken Cochrane](https://github.com/kencochrane)
- [Kevin Wetzels](https://github.com/roam)
- [David Cramer](https://github.com/dcramer)
- [Mark Philpot](https://github.com/griphiam)
- [Brad Chen](https://github.com/vvasabi)
- [ccouturi](https://github.com/ccouturi)
- [Colin Hebert](https://github.com/ColinHebert)

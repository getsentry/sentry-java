# Raven

[![Build Status](https://travis-ci.org/getsentry/raven-java.svg?branch=master)](https://travis-ci.org/getsentry/raven-java) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.getsentry.raven/raven-all/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.getsentry.raven/raven-all)

Raven is the Java client for [Sentry](https://www.getsentry.com/).
Raven relies on the most popular logging libraries to capture and convert logs
before sending details to a Sentry instance.

 - [`java.util.logging`](http://docs.oracle.com/javase/7/docs/technotes/guides/logging/index.html)
 support is provided by the main project [raven](raven)
 - [log4j](https://logging.apache.org/log4j/1.2/) support is provided in [raven-log4j](raven-log4j)
 - [log4j2](https://logging.apache.org/log4j/2.x/) can be used with [raven-log4j2](raven-log4j2)
 - [logback](http://logback.qos.ch/) support is provided in [raven-logback](raven-logback)

While it's **strongly recommended to use one of the supported logging
frameworks** to capture and send messages to Sentry, it is also possible to do so
manually with the main project [raven](raven).

Raven supports both HTTP and HTTPS as transport protocols to the Sentry
instance.

Support for [Google App Engine](https://appengine.google.com/) is provided in [raven-appengine](raven-appengine)

## Maven
Stable versions of Raven are available on the
[central Maven Repository](https://search.maven.org) under the `com.getsentry`
groupId. (NOTE: This is change from the previous `net.kencochrane` groupId)

Please see individual module `README`s for more information.

### Snapshot versions
Newer (but less stable) versions (AKA snapshots) are available in Sonatype's
snapshot repository.

To use it with maven, add the following repository:

```xml
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
```

## Android
Raven also works on Android. For integration details, see the [raven-android README](https://github.com/getsentry/raven-java/blob/master/raven-android/README.md).

## HTTP Request Context
If the runtime environment utilizes Servlets, events that are created during
the processing of an HTTP request will include additional contextual data about
that active request, such as the URL, method, parameters, and other data. (This
feature requires version 2.4 the Servlet API.)

## Connection and protocol
It is possible to send events to Sentry over different protocols, depending
on the security and performance requirements.

### HTTP
The most common way to send events to Sentry is via HTTP, this can be done by
using a DSN of this form:

    http://public:private@host:port/1

If not provided, the port will default to `80`.

### HTTPS
It is possible to use an encrypted connection to Sentry via HTTPS:

    https://public:private@host:port/1

If not provided, the port will default to `443`.

### HTTPS (naive)
If the certificate used over HTTPS is a wildcard certificate (which is not
handled by every version of Java), and the certificate isn't added to the
truststore, you can add a protocol setting to tell the client to be
naive and ignore hostname verification:

    naive+https://public:private@host:port/1

### Proxying HTTP(S) connections
If your application needs to send outbound requests through an HTTP proxy,
you can configure the proxy information via JVM networking properties or
as part of the Sentry DSN.

For example, using JVM networking properties (affects the entire JVM process),

```
java \
  # if you are using the HTTP protocol \
  -Dhttp.proxyHost=proxy.example.com \
  -Dhttp.proxyPort=8080 \
  \
  # if you are using the HTTPS protocol \
  -Dhttps.proxyHost=proxy.example.com \
  -Dhttps.proxyPort=8080 \
  \
  # relevant to both HTTP and HTTPS
  -Dhttp.nonProxyHosts=”localhost|host.example.com” \
  \
  MyApp
```

See [Java Networking and
Proxies](http://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html)
for more information about the proxy properties.

Alternatively, using the Sentry DSN (only affects the Sentry HTTP client,
useful inside shared application containers),

    http://public:private@host:port/1?raven.http.proxy.host=proxy.example.com&raven.http.proxy.port=8080

## Options
It is possible to enable some options by adding data to the query string of the
DSN:

    http://public:private@host:port/1?option1=value1&option2&option3=value3

Some options do not require a value, just being declared signifies that the
option is enabled.

### Async connection
In order to avoid performance issues due to a large amount of logs being
generated or a slow connection to the Sentry server, an asynchronous connection
is set up, using a low priority thread pool to submit events to Sentry.

To disable the async mode, add `raven.async=false` to the DSN:

    http://public:private@host:port/1?raven.async=false

#### Graceful Shutdown (advanced)
In order to shutdown the asynchronous connection gracefully, a `ShutdownHook`
is created. By default, the asynchronous connection is given 1 second
to shutdown gracefully, but this can be adjusted via
`raven.async.shutdowntimeout` (represented in milliseconds):

    http://public:private@host:port/1?raven.async.shutdowntimeout=5000

The special value `-1` can be used to disable the timeout and wait
indefinitely for the executor to terminate.

The `ShutdownHook` could lead to memory leaks in an environment where
the life cycle of Raven doesn't match the life cycle of the JVM.

An example would be in a JEE environment where the application using Raven
could be deployed and undeployed regularly.

To avoid this behaviour, it is possible to disable the graceful shutdown.
This might lead to some log entries being lost if the log application
doesn't shut down the Raven instance nicely.

The option to do so is `raven.async.gracefulshutdown`:

    http://public:private@host:port/1?raven.async.gracefulshutdown=false

#### Queue size (advanced)
The default queue used to store unprocessed events is limited to 50
items. Additional items added once the queue is full are dropped and
never sent to the Sentry server.
Depending on the environment (if the memory is sparse) it is important to be
able to control the size of that queue to avoid memory issues.

It is possible to set a maximum with the option `raven.async.queuesize`:

    http://public:private@host:port/1?raven.async.queuesize=100

This means that if the connection to the Sentry server is down, only the 100
most recent events will be stored and processed as soon as the server is back up.

The special value `-1` can be used to enable an unlimited queue. Beware
that network connectivity or Sentry server issues could mean your process
will run out of memory.

#### Threads count (advanced)
By default the thread pool used by the async connection contains one thread per
processor available to the JVM.

It's possible to manually set the number of threads (for example if you want
only one thread) with the option `raven.async.threads`:

    http://public:private@host:port/1?raven.async.threads=1

#### Threads priority (advanced)
In most cases sending logs to Sentry isn't as important as an application
running smoothly, so the threads have a
[minimal priority](http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY).

It is possible to customise this value to increase the priority of those threads
with the option `raven.async.priority`:

    http://public:private@host:port/1?raven.async.priority=10

### Buffering to disk upon network error
Raven can be configured to write events to a specified directory on disk
anytime communication with the Sentry server fails with the `raven.buffer.dir`
option. If the directory doesn't exist, Raven will attempt to create it
on startup and may therefore need write permission on the parent directory.
Raven always requires write permission on the buffer directory itself.

    http://public:private@host:port/1?raven.buffer.dir=raven-events

The maximum number of events that will be stored on disk defaults to 50,
but can also be configured with the option `raven.buffer.size`:

    http://public:private@host:port/1?raven.buffer.size=100

If a buffer directory is provided, a background thread will periodically
attempt to re-send the events that are found on disk. By default it will
attempt to send events every 60 seconds. You can change this with the
`raven.buffer.flushtime` option (in milliseconds):

    http://public:private@host:port/1?raven.buffer.flushtime=10000

#### Graceful Shutdown (advanced)
In order to shutdown the buffer flushing thread gracefully, a `ShutdownHook`
is created. By default, the buffer flushing thread is given 1 second
to shutdown gracefully, but this can be adjusted via
`raven.buffer.shutdowntimeout` (represented in milliseconds):

    http://public:private@host:port/1?raven.buffer.shutdowntimeout=5000

The special value `-1` can be used to disable the timeout and wait
indefinitely for the executor to terminate.

The `ShutdownHook` could lead to memory leaks in an environment where
the life cycle of Raven doesn't match the life cycle of the JVM.

An example would be in a JEE environment where the application using Raven
could be deployed and undeployed regularly.

To avoid this behaviour, it is possible to disable the graceful shutdown
by setting the `raven.buffer.gracefulshutdown` option:

    http://public:private@host:port/1?raven.buffer.gracefulshutdown=false

### Inapp classes
Sentry differentiate `in_app` stack frames (which are directly related to your application)
and the "not `in_app`" ones.
This difference is visible in the Sentry web interface where only the `in_app`
frames are displayed by default.

#### Same frame as enclosing exception
Raven can use the `in_app` system to hide frames in the context of chained exceptions.

Usually when a StackTrace is printed, the result looks like this:

    HighLevelException: MidLevelException: LowLevelException
            at Main.a(Main.java:13)
            at Main.main(Main.java:4)
    Caused by: MidLevelException: LowLevelException
            at Main.c(Main.java:23)
            at Main.b(Main.java:17)
            at Main.a(Main.java:11)
            ... 1 more
    Caused by: LowLevelException
            at Main.e(Main.java:30)
            at Main.d(Main.java:27)
            at Main.c(Main.java:21)
            ... 3 more

Some frames are replaced by the `... N more` line as they are the same frames
as in the enclosing exception.

To enable a similar behaviour from Raven use the `raven.stacktrace.hidecommon` option.

    http://public:private@host:port/1?raven.stacktrace.hidecommon

#### Hide frames based on the class name
Raven can also mark some frames as `in_app` based on the name of the class.

This can be used to hide parts of the stacktrace that are irrelevant to the problem
for example the stack frames in the `java.util` package will not help determining
what the problem was and will just create a longer stacktrace.

Currently this is not configurable (see #49) and some packages are ignored by default:

- `com.sun.*`
- `java.*`
- `javax.*`
- `org.omg.*`
- `sun.*`
- `junit.*`
- `com.intellij.rt.*`

### Compression
By default the content sent to Sentry is compressed and encoded in base64 before
being sent.
However, compressing and encoding the data adds a small CPU and memory hit which
might not be useful if the connection to Sentry is fast and reliable.

Depending on the limitations of the project (e.g. a mobile application with a
limited connection, Sentry hosted on an external network), it can be useful
to compress the data beforehand or not.

It's possible to manually enable/disable the compression with the option
`raven.compression`

    http://public:private@host:port/1?raven.compression=false

### Max message size
By default only the first 1000 characters of a message will be sent to
the server. This can be changed with the `raven.maxmessagelength` option.

    http://public:private@host:port/1?raven.maxmessagelength=1500

### Timeout (advanced)
A timeout is set to avoid blocking Raven threads because establishing a
connection is taking too long.

It's possible to manually set the timeout length with `raven.timeout`
(in milliseconds):

    http://public:private@host:port/1?raven.timeout=10000

## Custom RavenFactory
At times, you may require custom functionality that is not included in `raven-java`
already. The most common way to do this is to create your own RavenFactory instance
as seen in the example below.

```java
public class MyRavenFactory extends DefaultRavenFactory {

    @Override
    public Raven createRavenInstance(Dsn dsn) {
        Raven raven = new Raven();
        raven.setConnection(createConnection(dsn));

        /*
        Create and use the ForwardedAddressResolver, which will use the
        X-FORWARDED-FOR header for the remote address if it exists.
         */
        ForwardedAddressResolver forwardedAddressResolver = new ForwardedAddressResolver();
        raven.addBuilderHelper(new HttpEventBuilderHelper(forwardedAddressResolver));

        return raven;
    }

}
```

You'll need to add a `ServiceLoader` provider file to your project at
`src/main/resources/META-INF/services/com.getsentry.raven.RavenFactory` that contains
the name of your class so that it will be considered as a candidate `RavenFactory`. For an example, see
[how we configure the DefaultRavenFactory itself](https://github.com/getsentry/raven-java/blob/master/raven/src/main/resources/META-INF/services/com.getsentry.raven.RavenFactory).

Finally, see the `README` for the logger integration you use to find out how to
configure it to use your custom `RavenFactory`.

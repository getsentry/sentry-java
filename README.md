# Raven

[![Build Status](https://secure.travis-ci.org/kencochrane/raven-java.png?branch=master)](https://travis-ci.org/kencochrane/raven-java)

Raven is the Java client for [Sentry](https://www.getsentry.com/).
Raven relies on the most popular logging libraries to capture and convert logs
before sending details to a Sentry instance.

 - [`java.util.logging`](http://docs.oracle.com/javase/7/docs/technotes/guides/logging/index.html)
 support is provided by the main project [raven](raven)
 - [log4j](https://logging.apache.org/log4j/1.2/) support is provided in [raven-log4j](raven-log4j)
 - [log4j2](https://logging.apache.org/log4j/2.x/) can be used with [raven-log4j2](raven-log4j2)
 - [logback](http://logback.qos.ch/) support is provided in [raven-logback](raven-logback)

While it's **strongly recommended to use one of the supported logging
frameworks** to capture and send messages to Sentry, a it is possible to do so
manually with the main project [raven](raven).

Raven supports both HTTP(S) and UDP as transport protocols to the Sentry
instance.


## Sentry Protocol and Raven versions
Since 2.0, the major version of raven matches the version of the Sentry protocol.

| Raven version | Protocol version | Sentry version |
| ------------- | ---------------- | -------------- |
| Raven 2.x     | V2               | >= 2.0         |
| Raven 3.x     | V3               | >= 5.1         |
| Raven 4.x     | V4               | >= 6.0         |
| Raven 5.x(dev)| V5               | >= 6.4         |


Each release of Sentry supports the last two version of the protocol
(i.e. Sentry 6.4.2 supports both the protocol V5 and V4), for this reason, only
the two last stable versions of Raven are actively maintained.

### Snapshot versions
While the stable versions of raven are available on the
[central Maven Repository](https://search.maven.org), newer (but less stable)
versions (AKA snapshots) are available in Sonatype's snapshot repository.

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

Raven works on Android, and relies on the
[ServiceLoader](https://developer.android.com/reference/java/util/ServiceLoader.html)
system which uses the content of `META-INF/services`.
This is used to declare the `RavenFactory` implementations (to allow more
control over the automatically generated instances of `Raven`) in
`META-INF/services/net.kencochrane.raven.RavenFactory`.

Unfortunately, when the APK is build, the content of `META-INF/services` of
the dependencies is lost, this prevent Raven to work properly.
Solutions exist for that problem:

 - Use [maven-android-plugin](https://code.google.com/p/maven-android-plugin/)
 which has already solved this
[problem](https://code.google.com/p/maven-android-plugin/issues/detail?id=97)
 - Create manually a `META-INF/services/net.kencochrane.raven.RavenFactory` for
 the project which will contain the  canonical name of of implementation of
 `RavenFactory` (ie. `net.kencochrane.raven.DefaultRavenFactory`).
 - Register manually the `RavenFactory` when the application starts:

 ```java
 RavenFactory.registerFactory(new DefaultRavenFactory());
 ```

## Connection and protocol
It is possible to send events to Sentry over different protocols, depending
on the security and performance requirements.
So far Sentry accepts HTTP(S) and UDP which are both fully supported by
Raven.

### HTTP
The most common way send events to Sentry is through HTTP, this can be done by
using a DSN of this form:

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

Some options do not require a value, just being declared signifies that the
option is enabled.

### Async connection
In order to avoid performance issues due to a large amount of logs being
generated or a slow connection to the Sentry server, an asynchronous connection
is set up, using a low priority thread pool to submit events to Sentry.

To disable the async mode, add `raven.async=false` to the DSN:

    http://public:private@host:port/1?raven.async=false

#### Queue size (advanced)
The default queue used to store the not yet processed events doesn't have a
limit.
Depending on the environment (if the memory is sparse) it is important to be
able to control the size of that queue to avoid memory issues.

It is possible to set a maximum with the option `raven.async.queuesize`:

    http://public:private@host:port/1?raven.async.queuesize=100

This means that if the connection to the Sentry server is down, only the first
100 events will be stored and be processed as soon as the server is back up.

#### Threads count (advanced)
By default the thread pool used by the async connection contains one thread per
processor available to the JVM (more threads wouldn't be useful).

It's possible to manually set the number of threads (for example if you want
only one thread) with the option `raven.async.threads`:

    http://public:private@host:port/1?raven.async.threads=1

#### Threads priority (advanced)
As in most cases sending logs to Sentry isn't as important as an application
running smoothly, the threads have a
[minimal priority](http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#MIN_PRIORITY).

It is possible to customise this value to increase the priority of those threads
with the option `raven.async.priority`:

    http://public:private@host:port/1?raven.async.priority=10

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

To enable a similar behaviour from raven use the `raven.stacktrace.hidecommon` option.

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
This operation allows to send a smaller amount of data for each event.
However compressing and encoding the data adds a CPU and memory overhead which
might not be useful if the connection to Sentry is fast and reliable.

Depending on the limitations of the project (ie: a mobile application with a
limited connection, Sentry hosted on an external network), it can be interesting
to compress the data beforehand or not.

It's possible to manually enable/disable the compression with the option
`raven.compression`

    http://public:private@host:port/1?raven.compression=false

### Timeout (advanced)
To avoid blocking the thread because of a connection taking too much time, a
timeout can be set by the connection.

By default the connection will set up its own timeout, but it's possible to
manually set one with `raven.timeout` (in milliseconds):

    http://public:private@host:port/1?raven.timeout=10000

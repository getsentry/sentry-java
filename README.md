# Raven-Java

Raven-Java is a Java client for [Sentry](https://www.getsentry.com/). Besides a regular client you can use within your application code, Raven-Java also provides the `raven-log4j` package you can use to send logging to Sentry via [log4j](http://logging.apache.org/log4j/) and the `raven-logback` package to do the same through [logback](http://logback.qos.ch/).

Raven-Java supports both HTTP(S) and UDP transport of messages.

## Sentry Versions Supported
This client supports Sentry protocol version 2.0 (which is Sentry >= 2.0).

Since version 4.6.0 of Sentry, signed messages have been deprecated. If you still use an earlier version, have a look at the client options below to find out how to enable signed messages.

* * *

## Using the client

````java
import net.kencochrane.raven.Client
import net.kencochrane.raven.SentryDsn

public class Example {

	public static void main(String[] args) {
		// The DSN from Sentry: "http://public:private@host:port/1"
		String rawDsn = args[0];
		SentryDsn dsn = SentryDsn.build(rawDsn);
		Client client = new Client(dsn);
		client.captureMessage("Hello from Raven-Java!");
	}

}

````

A full example is available in `raven/src/main/test/net/kencochrane/raven/ClientExample`.

Note that `SentryDsn` will first examine the environment variables and system properties for a variable called `SENTRY_DSN`. If such a variable is available, *that* value will be used instead of the DSN you supply (the `rawDsn` variable in the example above).

This allows flexible usage of the library on PaaS providers such as Heroku. This also means the above example can be simplified to the following *if* you specify `SENTRY_DSN` in your:

- environment variables — e.g. `export SENTRY_DSN=yoursentrydsn` or `setenv SENTRYDSN yoursentrydsn`; or
- system properties — `-DSENTRY_DSN=yoursentrydsn`

````java
import net.kencochrane.raven.Client
import net.kencochrane.raven.SentryDsn

public class Example {

	public static void main(String[] args) {
		// DSN is determined by the client from system properties or env
		Client client = new Client();
		client.captureMessage("Hello from Raven-Java!");
	}

}

````

* * *

## Using the log4j appender
You can either utilize the `net.kencochrane.raven.log4j.SentryAppender` or `net.kencochrane.raven.log4j.AsyncSentryAppender` as an appender in your log4j configuration just like you would use any other appender.

    log4j.appender.sentry=net.kencochrane.raven.log4j.SentryAppender
    log4j.appender.sentry.sentryDsn=http://b4935bdd7862409:7a37d9ad47654281@localhost:8000/1

Like the client, these appenders will examine the system properties and environment variables for a better `SENTRY_DSN` candidate. Which log messages are ultimately sent to your Sentry instance, depends on the configuration of your Sentry appender. If you only want to send error messages, use the following:

    log4j.appender.sentry.Threshold=ERROR

### Asynchronous logging
If you use log4j's XML configuration, you can use its [AsyncAppender](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/AsyncAppender.html) to wrap Raven-Java's `SentryAppender`.

But even if you use a properties file to configure log4j, you can log asynchronously by using the `net.kencochrane.raven.log4j.AsyncSentryAppender` instead.

* * *

## Using the logback appender
Make sure to use the `raven-logback` artifact and add something like the following to your logback configuration:

	<appender name="SENTRY"
		class="net.kencochrane.raven.logback.SentryAppender">
		<sentryDsn>http://public:private@host:port/project</sentryDsn>
    </appender>

And don't forget to use the appender. For example:

    <root level="debug">
        <appender-ref ref="SENTRY" />
        <appender-ref ref="FILE" />
        <appender-ref ref="STDOUT" />
    </root>

* * *

## Client configuration
Client configuration is completely driven through the Sentry DSN. The DSN you can copy-paste from your Sentry instance looks like this:

    http://public:private@host:port/1

Changing the behavior of the client is done through the *scheme* and *querystring* of the DSN.

### Transport protocols

#### HTTPS
If you're using https, your DSN should look like this:

    https://public:private@host:port/1

#### Naive HTTPS
If you're using https with a wildcard certificate (which most Java versions can't handle) and you're too lazy
to add the certificate to your truststore, you can tell the client to be naive and allow it to ignore the
hostname verification:

    naive+https://public:private@host:port/1

#### UDP
Prefer udp? Change your DSN to this:

    udp://public:private@host:port/1

#### Asynchronous
The client can use a separate thread to actually send the messages to Sentry. To enable this feature, add `async+` to the scheme in your DSN:

    async+http://public:private@host:port/1
    # Or
    async+https://public:private@host:port/1
    # Or
    async+naive+https://public:private@host:port/1
    # Or
    async+udp://public:private@host:port/1

#### Adding other schemes
You can add your own custom transport and scheme through the `register` method of the client.

### Client options
More client configuration can be specified through the querystring of the DSN like this:

    http://public:private@host:port/1?optionA=true&optionB=20

#### Enabling signed messages
Signed messages have been deprecated in Sentry 4.6.0. If you're using an earlier version, you'll have to tell the client to sign messages through the option `raven.includeSignature`:

    http://public:private@host:port/1?raven.includeSignature=true

#### HTTP/HTTPS timeout
The default timeout for HTTP/HTTPS transport is set to 10 seconds. If you want to change this value, use the `raven.timeout` option to specify the timeout in milliseconds.

    http://public:private@host:port/1?raven.timeout=10000

#### Async queue configuration
When using the async transport (this is not the same as using the `AsyncSentryAppender`), you can configure the behavior of the underlying `java.util.concurrent.BlockingQueue` through the `raven.waitWhenFull` and `raven.capacity` options.

By default the client will **not** block when the queue is full and will use a queue at maximum capacity. If instead you want to use a blocking queue with a capacity of 20 messages, change your DSN to something like this:

    async+http://public:private@host:port/1?raven.waitWhenFull=true&raven.capacity=20

#### Enabling ServletJSONProcessor
In a servlet environment, Raven can append request information to logs sent to Sentry when logs are created on request threads. Information sent to Sentry include:

*   Request URL
*   POST parameters
*   Request headers
*   Cookies
*   Environment variables, including:
    *   Remote address
    *   Server name
    *   Server port
    *   Server protocol

Please be aware that sensitive information, such as user passwords or credit card numbers, may potentially be logged. Common security measures, such as protecting the Sentry installation, should be practiced. To enable this support, add the following line to Log4j configuration:

    log4j.appender.sentry.jsonProcessors=net.kencochrane.raven.ext.ServletJSONProcessor

Then, add the following lines to web.xml:

    <listener>
        <listener-class>
            net.kencochrane.raven.ext.RavenServletRequestListener
        </listener-class>
    </listener>

* * *

## Installation

This version isn't available in the Central Maven repository yet. The easiest
way to get started is to clone this repository and install the artifacts into
your local repository or proxy like Nexus:

    $ git clone https://github.com/kencochrane/raven-java.git
    $ cd raven-java
    $ mvn clean install

This will build and test the Raven client and Log4J appender and install it
into your local repository. You shouldn't worry about stacktraces appearing in
the output unless tests are failing; they are *supposed* to be there.

Then add the correct dependency to your POM file:

    <dependency>
        <groupId>net.kencochrane</groupId>
        <artifactId>raven</artifactId>
        <version>2.0-SNAPSHOT</version>
    </dependency>

Or if you simply want to log to Sentry from Log4J:

    <dependency>
        <groupId>net.kencochrane</groupId>
        <artifactId>raven-log4j</artifactId>
        <version>2.0-SNAPSHOT</version>
    </dependency>

Or Logback:

    <dependency>
        <groupId>net.kencochrane</groupId>
        <artifactId>raven-logback</artifactId>
        <version>2.0-SNAPSHOT</version>
    </dependency>

* * *

## History

- 2.0-SNAPSHOT
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

- Ken Cochrane (@KenCochrane)
- Kevin Wetzels (@roambe)
- David Cramer (@zeeg)
- Mark Philpot (@griphiam)
- Brad Chen (@vvasabi)
- @ccouturi
